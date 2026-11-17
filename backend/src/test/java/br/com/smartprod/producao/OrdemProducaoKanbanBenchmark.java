
package br.com.smartprod.producao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartprod_kanban_benchmark;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OrdemProducaoKanbanBenchmark {

    private static final int TOTAL_ORDENS = 500;
    private static final int REPETICOES = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrdemProducaoRepository ordemRepository;

    @Autowired
    private FichaTecnicaRepository fichaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @WithMockUser(roles = "GERENTE")
    void medirDesempenhoDoKanban() throws Exception {
        prepararDados();

        medir(
                "Todas as ordens",
                "/api/ordens-producao",
                TOTAL_ORDENS);

        medir(
                "Filtro por status",
                "/api/ordens-producao?status=PENDENTE",
                TOTAL_ORDENS);

        medir(
                "Filtro por data",
                "/api/ordens-producao?data=2026-11-20",
                TOTAL_ORDENS);

        medir(
                "Status e data",
                "/api/ordens-producao?status=PENDENTE&data=2026-11-20",
                TOTAL_ORDENS);
    }

    private void prepararDados() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            Usuario gerente = usuarioRepository.save(
                    new Usuario(
                            "Gerente Benchmark",
                            "gerente.benchmark@teste.com",
                            "hash",
                            Perfil.GERENTE));

            Usuario funcionario = usuarioRepository.save(
                    new Usuario(
                            "Funcionário Benchmark",
                            "funcionario.benchmark@teste.com",
                            "hash",
                            Perfil.FUNCIONARIO));

            FichaTecnica ficha = fichaRepository.save(
                    new FichaTecnica("Pão francês", 50));

            List<OrdemProducao> ordens = new ArrayList<>();

            for (int i = 0; i < TOTAL_ORDENS; i++) {
                ordens.add(new OrdemProducao(
                        ficha,
                        100,
                        Instant.parse("2026-11-20T14:00:00Z")
                                .plusSeconds(i),
                        funcionario,
                        gerente));
            }

            ordemRepository.saveAll(ordens);
            ordemRepository.flush();
        });

        System.out.println(
                "BENCHMARK: " + TOTAL_ORDENS
                        + " ordens preparadas.");
    }

    private void medir(
            String descricao,
            String url,
            int quantidadeEsperada) throws Exception {

        // Aquecimento da consulta e das classes utilizadas.
        for (int i = 0; i < 2; i++) {
            executarRequisicao(url, quantidadeEsperada);
        }

        long menorTempo = Long.MAX_VALUE;
        long maiorTempo = 0;
        long somaTempos = 0;

        for (int i = 0; i < REPETICOES; i++) {
            long inicio = System.nanoTime();

            executarRequisicao(url, quantidadeEsperada);

            long duracao = System.nanoTime() - inicio;

            menorTempo = Math.min(menorTempo, duracao);
            maiorTempo = Math.max(maiorTempo, duracao);
            somaTempos += duracao;
        }

        double mediaMs = (somaTempos / (double) REPETICOES)
                / TimeUnit.MILLISECONDS.toNanos(1);

        double minimoMs = menorTempo / 1_000_000.0;
        double maximoMs = maiorTempo / 1_000_000.0;

        System.out.printf(
                "BENCHMARK | %s | média: %.2f ms | mínimo: %.2f ms | máximo: %.2f ms%n",
                descricao,
                mediaMs,
                minimoMs,
                maximoMs);
    }

    private void executarRequisicao(
            String url,
            int quantidadeEsperada) throws Exception {

        String resposta = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resposta).isNotBlank();

        // Confirma que a resposta contém a quantidade esperada
        // de ordens, sem adicionar a desserialização ao tempo medido.
        long totalIds = java.util.regex.Pattern
                .compile("\"id\"\\s*:")
                .matcher(resposta)
                .results()
                .count();

        // A resposta contém IDs da ordem, responsável e criador.
        assertThat(totalIds).isEqualTo(quantidadeEsperada * 3L);
    }
}
