
package br.com.smartprod.producao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.smartprod.producao.dto.OrdemProducaoCriacaoRequest;
import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class OrdemProducaoService {

    private final OrdemProducaoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final FichaTecnicaRepository fichaTecnicaRepository;

    public OrdemProducaoService(
            OrdemProducaoRepository repository,
            UsuarioRepository usuarioRepository,
            FichaTecnicaRepository fichaTecnicaRepository) {

        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.fichaTecnicaRepository = fichaTecnicaRepository;
    }

    @Transactional(readOnly = true)
    public List<OrdemProducao> listarTodas() {
        return repository.findAllByOrderByHorarioEntregaAsc();
    }

    @Transactional(readOnly = true)
    public List<OrdemProducao> listarPorStatus(StatusOrdemProducao status) {
        return repository.findByStatusOrderByHorarioEntregaAsc(status);
    }

    @Transactional(readOnly = true)
    public List<OrdemProducao> listarPorFiltros(
            StatusOrdemProducao status,
            LocalDate data) {

        if (data == null && status == null) {
            ZoneId zona = ZoneId.of("America/Sao_Paulo");
            LocalDate hoje = LocalDate.now(zona);

            Instant inicio = hoje.atStartOfDay(zona).toInstant();
            Instant fim = hoje.plusDays(1)
                    .atStartOfDay(zona)
                    .toInstant();

            return repository.findOrdensKanbanPadrao(
                    StatusOrdemProducao.CONCLUIDA,
                    inicio,
                    fim);
        }

        if (data == null) {
            return listarPorStatus(status);
        }

        ZoneId zona = ZoneId.of("America/Sao_Paulo");

        Instant inicio = data.atStartOfDay(zona).toInstant();
        Instant fim = data.plusDays(1)
                .atStartOfDay(zona)
                .toInstant();

        return status == null
                ? repository
                        .findByHorarioEntregaGreaterThanEqualAndHorarioEntregaLessThanOrderByHorarioEntregaAsc(
                                inicio, fim)
                : repository
                        .findByStatusAndHorarioEntregaGreaterThanEqualAndHorarioEntregaLessThanOrderByHorarioEntregaAsc(
                                status, inicio, fim);
    }

    @Transactional(readOnly = true)
    public OrdemProducao buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ordem de produção não encontrada."));
    }

    @Transactional(readOnly = true)
    public Usuario buscarResponsavelAtivo(Long id) {
        return usuarioRepository.findById(id)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuário responsável não encontrado ou inativo."));
    }

    @Transactional(readOnly = true)
    public Usuario buscarGerenteAtivo(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Gerente criador não encontrado ou inativo."));

        if (usuario.getPerfil() != Perfil.GERENTE) {
            throw new IllegalArgumentException(
                    "Somente um gerente pode criar uma ordem de produção.");
        }

        return usuario;
    }

    @Transactional
    public OrdemProducao criar(
            OrdemProducaoCriacaoRequest request,
            Long gerenteId) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Os dados da ordem são obrigatórios.");
        }

        Usuario gerente = buscarGerenteAtivo(gerenteId);
        Usuario responsavel = buscarResponsavelAtivo(request.responsavelId());

        FichaTecnica fichaTecnica = fichaTecnicaRepository
                .findById(request.fichaTecnicaId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ficha técnica não encontrada."));

        OrdemProducao ordem = new OrdemProducao(
                fichaTecnica,
                request.quantidadeUnidades(),
                request.horarioEntrega(),
                responsavel,
                gerente);

        return repository.save(ordem);
    }

    @Transactional
    public OrdemProducao iniciar(Long id) {
        OrdemProducao ordem = buscarPorId(id);
        ordem.iniciar();
        return ordem;
    }

    @Transactional
    public OrdemProducao concluir(Long id, Integer quantidadeProduzida) {
        OrdemProducao ordem = buscarPorId(id);
        ordem.concluir(quantidadeProduzida);
        return ordem;
    }
}
