package br.com.smartprod.receita;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.InsumoRepository;
import br.com.smartprod.receita.dto.FichaTecnicaRequest;
import br.com.smartprod.receita.dto.FichaTecnicaRequest.IngredienteRequest;
import br.com.smartprod.receita.dto.FichaTecnicaRequest.PassoRequest;
import br.com.smartprod.receita.dto.FichaTecnicaResponse;
import br.com.smartprod.receita.dto.FichaTecnicaResumoResponse;

/**
 * Regras das fichas técnicas (RF005). A conversão para DTO acontece dentro da transação, porque os
 * ingredientes e os passos são carregados sob demanda. Os erros saem como {@link ResponseStatusException}.
 */
@Service
public class FichaTecnicaService {

    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final PorcentagemPadeiroService porcentagemPadeiroService;
    private final InsumoRepository insumoRepository;

    public FichaTecnicaService(FichaTecnicaRepository fichaTecnicaRepository,
            PorcentagemPadeiroService porcentagemPadeiroService, InsumoRepository insumoRepository) {
        this.fichaTecnicaRepository = fichaTecnicaRepository;
        this.porcentagemPadeiroService = porcentagemPadeiroService;
        this.insumoRepository = insumoRepository;
    }

    @Transactional(readOnly = true)
    public List<FichaTecnicaResumoResponse> listar() {
        return fichaTecnicaRepository.findAllByOrderByNomeAsc().stream()
                .map(FichaTecnicaResumoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public FichaTecnicaResponse buscar(Long id) {
        return resposta(buscarEntidade(id));
    }

    /** RF005: cadastra a ficha com ingredientes e passos. Numa ficha nova só entram insumos ativos. */
    @Transactional
    public FichaTecnicaResponse criar(FichaTecnicaRequest requisicao) {
        FichaTecnica ficha = new FichaTecnica(requisicao.nome().trim(), requisicao.rendimentoPadrao());
        preencher(ficha, requisicao, Set.of());
        fichaTecnicaRepository.save(ficha);
        return resposta(ficha);
    }

    /**
     * RF005: edita nome, rendimento, ingredientes e passos (as listas são substituídas inteiras). Um insumo
     * que já estava na ficha pode continuar nela mesmo depois de inativado; insumo novo precisa estar ativo.
     */
    @Transactional
    public FichaTecnicaResponse atualizar(Long id, FichaTecnicaRequest requisicao) {
        FichaTecnica ficha = buscarEntidade(id);
        Set<Long> insumosQueJaEstavam = ficha.getIngredientes().stream()
                .map(ingrediente -> ingrediente.getInsumo().getId())
                .collect(toSet());
        ficha.atualizar(requisicao.nome().trim(), requisicao.rendimentoPadrao());
        preencher(ficha, requisicao, insumosQueJaEstavam);
        fichaTecnicaRepository.save(ficha);
        return resposta(ficha);
    }

    FichaTecnica buscarEntidade(Long id) {
        return fichaTecnicaRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Ficha técnica não encontrada."));
    }

    private FichaTecnicaResponse resposta(FichaTecnica ficha) {
        List<BigDecimal> percentuais = porcentagemPadeiroService.calcular(ficha.getIngredientes());
        return FichaTecnicaResponse.de(ficha, percentuais);
    }

    private void preencher(FichaTecnica ficha, FichaTecnicaRequest requisicao, Set<Long> insumosQuePodemEstarInativos) {
        List<Long> ids = requisicao.ingredientes().stream().map(IngredienteRequest::insumoId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw invalido("Cada insumo pode aparecer só uma vez na ficha.");
        }
        Map<Long, Insumo> insumos = insumoRepository.findAllById(ids).stream()
                .collect(toMap(Insumo::getId, Function.identity()));

        List<FichaIngrediente> ingredientes = new ArrayList<>();
        for (IngredienteRequest ingrediente : requisicao.ingredientes()) {
            Insumo insumo = insumos.get(ingrediente.insumoId());
            if (insumo == null) {
                throw invalido("Insumo " + ingrediente.insumoId() + " não encontrado.");
            }
            if (!insumo.isAtivo() && !insumosQuePodemEstarInativos.contains(insumo.getId())) {
                throw invalido("O insumo \"" + insumo.getNome() + "\" está inativo e não pode entrar na ficha.");
            }
            ingredientes.add(new FichaIngrediente(ficha, insumo, ingrediente.quantidadeBase(), ingrediente.ehFarinha()));
        }

        Set<Integer> ordens = new HashSet<>();
        if (!requisicao.passos().stream().allMatch(passo -> ordens.add(passo.ordem()))) {
            throw invalido("Dois passos não podem ter a mesma ordem.");
        }
        List<PassoPreparo> passos = requisicao.passos().stream()
                .sorted(Comparator.comparing(PassoRequest::ordem))
                .map(passo -> new PassoPreparo(ficha, passo.ordem(), passo.etapa().trim(), passo.descricao().trim(),
                        passo.duracaoSegundos()))
                .toList();

        ficha.substituirIngredientes(ingredientes);
        ficha.substituirPassos(passos);
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}