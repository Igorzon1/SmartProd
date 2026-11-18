
package br.com.smartprod.producao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.InsumoRepository;
import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.producao.dto.OrdemProducaoExecucaoResponse;
import br.com.smartprod.receita.FichaIngrediente;
import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;
import br.com.smartprod.receita.PassoPreparo;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartprod_execucao_integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("dev")
class OrdemProducaoExecucaoIntegrationTest {

    @Autowired
    private OrdemProducaoExecucaoService execucaoService;

    @Autowired
    private OrdemProducaoRepository ordemRepository;

    @Autowired
    private FichaTecnicaRepository fichaTecnicaRepository;

    @Autowired
    private InsumoRepository insumoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void deveCarregarFichaPersistidaECalcularExecucao() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long[] ids = transactionTemplate.execute(status -> {
            UnidadeMedida unidade = UnidadeMedida.values()[0];

            Insumo farinha = insumoRepository.saveAndFlush(
                    new Insumo(
                            "Farinha de trigo",
                            unidade,
                            new BigDecimal("5.0000")));

            Usuario funcionario = usuarioRepository.saveAndFlush(
                    new Usuario(
                            "Funcionário Integração",
                            "funcionario.integracao@teste.com",
                            "hash-teste",
                            Perfil.FUNCIONARIO));

            FichaTecnica ficha = new FichaTecnica(
                    "Pão francês",
                    50);

            ficha.adicionarIngrediente(
                    new FichaIngrediente(
                            ficha,
                            farinha,
                            new BigDecimal("2.5000"),
                            true));

            ficha.adicionarPasso(
                    new PassoPreparo(
                            ficha,
                            2,
                            "Modelagem",
                            "Modelar os pães",
                            600));

            ficha.adicionarPasso(
                    new PassoPreparo(
                            ficha,
                            1,
                            "Mistura",
                            "Misturar os ingredientes",
                            300));

            ficha = fichaTecnicaRepository.saveAndFlush(ficha);

            OrdemProducao ordem = new OrdemProducao(
                    ficha,
                    100,
                    Instant.parse("2026-11-20T14:00:00Z"),
                    funcionario,
                    funcionario);

            ordem = ordemRepository.saveAndFlush(ordem);

            return new Long[] {
                    ordem.getId(),
                    ficha.getId(),
                    farinha.getId()
            };
        });

        assertThat(ids).isNotNull();

        Long ordemId = ids[0];
        Long fichaId = ids[1];
        Long insumoId = ids[2];

        OrdemProducaoExecucaoResponse resposta = execucaoService.buscarExecucao(ordemId);

        assertThat(resposta.ordemProducaoId())
                .isEqualTo(ordemId);

        assertThat(resposta.fichaTecnicaId())
                .isEqualTo(fichaId);

        assertThat(resposta.fichaNome())
                .isEqualTo("Pão francês");

        assertThat(resposta.quantidadeUnidades())
                .isEqualTo(100);

        assertThat(resposta.status())
                .isEqualTo(StatusOrdemProducao.PENDENTE);

        assertThat(resposta.ingredientes()).hasSize(1);

        var ingrediente = resposta.ingredientes().get(0);

        assertThat(ingrediente.insumoId())
                .isEqualTo(insumoId);

        assertThat(ingrediente.nome())
                .isEqualTo("Farinha de trigo");

        assertThat(ingrediente.quantidade())
                .isEqualByComparingTo("5.000");

        assertThat(ingrediente.unidadeMedida())
                .isEqualTo(UnidadeMedida.values()[0]);

        assertThat(resposta.passos()).hasSize(2);

        assertThat(resposta.passos().get(0).ordem())
                .isEqualTo(1);

        assertThat(resposta.passos().get(0).etapa())
                .isEqualTo("Mistura");

        assertThat(resposta.passos().get(0).duracaoSegundos())
                .isEqualTo(300);

        assertThat(resposta.passos().get(1).ordem())
                .isEqualTo(2);

        assertThat(resposta.passos().get(1).etapa())
                .isEqualTo("Modelagem");

        assertThat(resposta.passos().get(1).duracaoSegundos())
                .isEqualTo(600);
    }

}
