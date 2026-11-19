package br.com.smartprod.producao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;

/**
 * {@code POST /api/ordens-producao} de ponta a ponta, com banco H2 próprio: a entrega precisa ser de agora
 * em diante. O Gerente da seed do perfil dev (id 1) abre a OP e é o responsável.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:smartprod_criacao_op;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OrdemProducaoCriacaoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrdemProducaoRepository ordemRepository;

    @Autowired
    private FichaTecnicaRepository fichaRepository;

    @Test
    @WithMockUser(username = "1", roles = "GERENTE")
    void entregaNoPassadoRetorna400ENaoCriaAOrdem() throws Exception {
        long antes = ordemRepository.count();

        mockMvc.perform(post("/api/ordens-producao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Instant.now().minus(Duration.ofDays(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("O horário de entrega já passou. Escolha uma data e hora a partir de agora."));

        assertThat(ordemRepository.count()).isEqualTo(antes);
    }

    @Test
    @WithMockUser(username = "1", roles = "GERENTE")
    void entregaNoFuturoCriaAOrdem() throws Exception {
        mockMvc.perform(post("/api/ordens-producao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Instant.now().plus(Duration.ofHours(3)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    private String corpo(Instant entrega) {
        FichaTecnica ficha = fichaRepository.save(new FichaTecnica("Pão do teste", 10));
        return """
                {
                  "fichaTecnicaId": %d,
                  "quantidadeUnidades": 20,
                  "horarioEntrega": "%s",
                  "responsavelId": 1
                }
                """.formatted(ficha.getId(), entrega.truncatedTo(ChronoUnit.SECONDS));
    }
}
