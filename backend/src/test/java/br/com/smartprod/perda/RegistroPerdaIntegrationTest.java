
package br.com.smartprod.perda;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.smartprod.producao.OrdemProducao;
import br.com.smartprod.producao.OrdemProducaoRepository;
import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartprod_perda_integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RegistroPerdaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistroPerdaService registroPerdaService;

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
    void deveListarPerdaRegistradaPorOutroFuncionario()
            throws Exception {

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        Long[] ids = transactionTemplate.execute(status -> {

            Usuario gerente = usuarioRepository.saveAndFlush(
                    new Usuario(
                            "Gerente Integracao",
                            "gerente.perda.integracao@teste.com",
                            "hash-teste",
                            Perfil.GERENTE));

            Usuario funcionarioF1 = usuarioRepository.saveAndFlush(
                    new Usuario(
                            "Funcionario F1",
                            "funcionario.f1.perda@teste.com",
                            "hash-teste",
                            Perfil.FUNCIONARIO));

            Usuario funcionarioF2 = usuarioRepository.saveAndFlush(
                    new Usuario(
                            "Funcionario F2",
                            "funcionario.f2.perda@teste.com",
                            "hash-teste",
                            Perfil.FUNCIONARIO));

            FichaTecnica ficha = fichaRepository.saveAndFlush(
                    new FichaTecnica(
                            "Pao frances",
                            50));

            OrdemProducao ordem = new OrdemProducao(
                    ficha,
                    100,
                    Instant.parse("2026-11-20T14:00:00Z"),
                    funcionarioF1,
                    gerente);

            ordem.iniciar();
            ordem = ordemRepository.saveAndFlush(ordem);

            return new Long[] {
                    ordem.getId(),
                    funcionarioF2.getId()
            };
        });

        assertThat(ids).isNotNull();

        Long ordemId = ids[0];
        Long funcionarioF2Id = ids[1];

        // F2 nao e o responsavel nem o criador da OP.
        // O registro ocorre apos a transacao de preparacao.
        RegistroPerda perda = registroPerdaService.registrar(
                ordemId,
                "Forno",
                new BigDecimal("2.500"),
                10,
                "Fornada queimada",
                funcionarioF2Id);

        assertThat(perda.getId()).isNotNull();

        // A consulta acontece fora da transacao anterior.
        // Com open-in-view=false, os dados de registradoPor
        // precisam estar carregados para montar o DTO.
        mockMvc.perform(get(
                        "/api/ordens-producao/{ordemId}/perdas",
                        ordemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id")
                        .value(perda.getId()))
                .andExpect(jsonPath("$[0].pesoKg").value(2.5))
                .andExpect(jsonPath("$[0].unidades").value(10))
                .andExpect(jsonPath("$[0].registradoPorId")
                        .value(funcionarioF2Id))
                .andExpect(jsonPath("$[0].registradoPorNome")
                        .value("Funcionario F2"));
    }
}
