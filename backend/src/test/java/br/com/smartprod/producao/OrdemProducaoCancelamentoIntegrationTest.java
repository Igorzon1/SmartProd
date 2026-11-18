package br.com.smartprod.producao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;

/**
 * {@code DELETE /api/ordens-producao/{id}} de ponta a ponta, com banco H2 próprio: a OP pendente sai do
 * banco, a que já começou fica, e só o Gerente pode cancelar.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:smartprod_cancelamento;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OrdemProducaoCancelamentoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrdemProducaoRepository ordemRepository;

    @Autowired
    private FichaTecnicaRepository fichaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @WithMockUser(username = "1", roles = "GERENTE")
    void gerenteCancelaOrdemPendente() throws Exception {
        Long id = salvarOrdem(false);

        mockMvc.perform(delete("/api/ordens-producao/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(ordemRepository.existsById(id)).isFalse();
    }

    @Test
    @WithMockUser(username = "1", roles = "GERENTE")
    void ordemQueJaComecouRetorna409EContinuaNoBanco() throws Exception {
        Long id = salvarOrdem(true);

        mockMvc.perform(delete("/api/ordens-producao/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", Matchers.startsWith("Só dá para cancelar uma OP pendente.")));

        assertThat(ordemRepository.existsById(id)).isTrue();
    }

    @Test
    @WithMockUser(username = "1", roles = "GERENTE")
    void ordemInexistenteRetorna404() throws Exception {
        mockMvc.perform(delete("/api/ordens-producao/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ordem de produção não encontrada."));
    }

    @Test
    @WithMockUser(username = "2", roles = "FUNCIONARIO")
    void funcionarioNaoCancela() throws Exception {
        Long id = salvarOrdem(false);

        mockMvc.perform(delete("/api/ordens-producao/{id}", id))
                .andExpect(status().isForbidden());

        assertThat(ordemRepository.existsById(id)).isTrue();
    }

    @Test
    void semLoginRecebe401() throws Exception {
        Long id = salvarOrdem(false);

        mockMvc.perform(delete("/api/ordens-producao/{id}", id))
                .andExpect(status().isUnauthorized());

        assertThat(ordemRepository.existsById(id)).isTrue();
    }

    private Long salvarOrdem(boolean iniciada) {
        Usuario gerente = usuarioRepository.save(new Usuario("Gerente do teste",
                "gerente." + UUID.randomUUID() + "@padaria.test", "hash", Perfil.GERENTE));
        FichaTecnica ficha = fichaRepository.save(new FichaTecnica("Pão do teste", 10));
        OrdemProducao ordem = new OrdemProducao(ficha, 20, Instant.parse("2026-11-20T14:00:00Z"), gerente, gerente);
        if (iniciada) {
            ordem.iniciar();
        }
        return ordemRepository.save(ordem).getId();
    }
}
