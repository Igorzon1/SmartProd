
package br.com.smartprod.producao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;


@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrdemProducaoRepositoryTest {

    @Autowired
    private OrdemProducaoRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private FichaTecnicaRepository fichaTecnicaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deveExibirNoKanbanApenasConcluidasDeHoje() {
        Usuario usuario = criarUsuario();

        ZoneId zona = ZoneId.of("America/Sao_Paulo");
        LocalDate hoje = LocalDate.now(zona);

        Instant inicio = hoje.atStartOfDay(zona).toInstant();
        Instant fim = hoje.plusDays(1)
                .atStartOfDay(zona)
                .toInstant();

        OrdemProducao pendente = criarOrdem(
                usuario, inicio.minusSeconds(86400));

        OrdemProducao emAndamento = criarOrdem(
                usuario, inicio.minusSeconds(3600));
        emAndamento.iniciar();

        OrdemProducao concluidaHoje = criarOrdem(
                usuario, inicio.plusSeconds(3600));
        concluidaHoje.iniciar();
        concluidaHoje.concluir(50);

        ReflectionTestUtils.setField(
                concluidaHoje,
                "concluidaEm",
                inicio.plusSeconds(7200));

        OrdemProducao concluidaOntem = criarOrdem(
                usuario, inicio.plusSeconds(10800));
        concluidaOntem.iniciar();
        concluidaOntem.concluir(50);

        ReflectionTestUtils.setField(
                concluidaOntem,
                "concluidaEm",
                inicio.minusSeconds(3600));

        repository.saveAllAndFlush(List.of(
                pendente,
                emAndamento,
                concluidaHoje,
                concluidaOntem));

        entityManager.clear();

        List<OrdemProducao> resultado = repository.findOrdensKanbanPadrao(
                StatusOrdemProducao.CONCLUIDA,
                inicio,
                fim);

        assertThat(resultado)
                .extracting(OrdemProducao::getId)
                .containsExactly(
                        pendente.getId(),
                        emAndamento.getId(),
                        concluidaHoje.getId());

        assertThat(resultado)
                .extracting(OrdemProducao::getId)
                .doesNotContain(concluidaOntem.getId());
    }

    @Test
    void deveBuscarOrdensDentroDoIntervaloDeData() {
        Usuario usuario = criarUsuario();

        LocalDate data = LocalDate.of(2026, 11, 10);
        Instant inicio = data.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant fim = data.plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        OrdemProducao ordem1 = criarOrdem(
                usuario, Instant.parse("2026-11-10T08:00:00Z"));

        OrdemProducao ordem2 = criarOrdem(
                usuario, Instant.parse("2026-11-10T15:00:00Z"));

        OrdemProducao ordem3 = criarOrdem(
                usuario, Instant.parse("2026-11-11T08:00:00Z"));

        repository.saveAllAndFlush(List.of(ordem1, ordem2, ordem3));

        List<OrdemProducao> resultado = repository
                .findByHorarioEntregaGreaterThanEqualAndHorarioEntregaLessThanOrderByHorarioEntregaAsc(
                        inicio, fim);

        assertThat(resultado)
                .hasSize(2)
                .containsExactly(ordem1, ordem2);
    }

    @Test
    void deveBuscarOrdensPorStatusEDentroDoIntervaloDeData() {
        Usuario usuario = criarUsuario();

        LocalDate data = LocalDate.of(2026, 11, 10);
        Instant inicio = data.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant fim = data.plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        OrdemProducao pendente = criarOrdem(
                usuario, Instant.parse("2026-11-10T08:00:00Z"));

        OrdemProducao emAndamento = criarOrdem(
                usuario, Instant.parse("2026-11-10T15:00:00Z"));

        emAndamento.iniciar();

        repository.saveAllAndFlush(List.of(pendente, emAndamento));

        List<OrdemProducao> resultado = repository
                .findByStatusAndHorarioEntregaGreaterThanEqualAndHorarioEntregaLessThanOrderByHorarioEntregaAsc(
                        StatusOrdemProducao.EM_ANDAMENTO, inicio, fim);

        assertThat(resultado)
                .hasSize(1)
                .containsExactly(emAndamento);
    }

    @Test
    void devePersistirOrdemVinculadaAFichaTecnica() {
        Usuario usuario = criarUsuario();

        FichaTecnica ficha = fichaTecnicaRepository.saveAndFlush(
                new FichaTecnica("Pão francês", 50));

        OrdemProducao ordem = new OrdemProducao(
                ficha,
                100,
                Instant.parse("2026-11-20T14:00:00Z"),
                usuario,
                usuario);

        OrdemProducao ordemSalva = repository.saveAndFlush(ordem);

        Long ordemId = ordemSalva.getId();
        Long fichaId = ficha.getId();

        entityManager.clear();

        assertThat(ordemId).isNotNull();
        assertThat(fichaId).isNotNull();

        OrdemProducao encontrada = repository.findById(ordemId)
                .orElseThrow();

        assertThat(encontrada.getFichaTecnica()).isNotNull();
        assertThat(encontrada.getFichaTecnica().getId())
                .isEqualTo(fichaId);

        assertThat(encontrada.getFichaTecnica().getNome())
                .isEqualTo("Pão francês");

        assertThat(encontrada.getQuantidadeUnidades())
                .isEqualTo(100);

        assertThat(encontrada.getStatus())
                .isEqualTo(StatusOrdemProducao.PENDENTE);
    }

    private Usuario criarUsuario() {
        return usuarioRepository.saveAndFlush(
                new Usuario(
                        "Funcionário Teste",
                        "funcionario@teste.com",
                        "hash-teste",
                        Perfil.FUNCIONARIO));
    }

    private OrdemProducao criarOrdem(
            Usuario usuario,
            Instant horarioEntrega) {

        FichaTecnica ficha = fichaTecnicaRepository.saveAndFlush(
                new FichaTecnica("Pão francês", 50));

        return new OrdemProducao(
                ficha,
                50,
                horarioEntrega,
                usuario,
                usuario);
    }
}
