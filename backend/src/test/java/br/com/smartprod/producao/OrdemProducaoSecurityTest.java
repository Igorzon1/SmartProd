
package br.com.smartprod.producao;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.smartprod.producao.dto.OrdemProducaoCriacaoRequest;
import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;

@SpringBootTest
@AutoConfigureMockMvc
class OrdemProducaoSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrdemProducaoService service;

    private static final String JSON_VALIDO = """
            {
                "fichaTecnicaId": 3,
                "quantidadeUnidades": 100,
                "horarioEntrega": "2026-11-20T14:00:00Z",
                "responsavelId": 2
            }
            """;

    @Test
    @WithMockUser(username = "1", roles = "GERENTE")
    void gerentePodeCriarOrdem() throws Exception {
        Usuario gerente = new Usuario(
                "Gerente",
                "gerente@padaria.com",
                "hash",
                Perfil.GERENTE);

        Usuario responsavel = new Usuario(
                "Funcionário",
                "funcionario@padaria.com",
                "hash",
                Perfil.FUNCIONARIO);

        FichaTecnica ficha = new FichaTecnica("Pão francês", 50);

        ReflectionTestUtils.setField(ficha, "id", 3L);

        OrdemProducao ordem = new OrdemProducao(
                ficha,
                100,
                Instant.parse("2026-11-20T14:00:00Z"),
                responsavel,
                gerente);

        ReflectionTestUtils.setField(ordem, "id", 10L);

        when(service.criar(
                any(OrdemProducaoCriacaoRequest.class),
                eq(1L)))
                .thenReturn(ordem);

        mockMvc.perform(post("/api/ordens-producao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location", "/api/ordens-producao/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.fichaTecnicaId").value(3))
                .andExpect(jsonPath("$.fichaNome").value("Pão francês"))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        verify(service).criar(
                any(OrdemProducaoCriacaoRequest.class),
                eq(1L));
    }

    @Test
    @WithMockUser(username = "2", roles = "FUNCIONARIO")
    void funcionarioNaoPodeCriarOrdem() throws Exception {
        mockMvc.perform(post("/api/ordens-producao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void usuarioNaoAutenticadoRecebe401() throws Exception {
        mockMvc.perform(post("/api/ordens-producao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "1", roles = "GERENTE")
    void requisicaoInvalidaRecebe400() throws Exception {
        String jsonInvalido = """
                {
                    "fichaTecnicaId": 3,
                    "quantidadeUnidades": 0,
                    "horarioEntrega": "2026-11-20T14:00:00Z",
                    "responsavelId": 2
                }
                """;

        mockMvc.perform(post("/api/ordens-producao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonInvalido))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
