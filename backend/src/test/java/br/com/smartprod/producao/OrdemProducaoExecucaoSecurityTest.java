
package br.com.smartprod.producao;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.producao.dto.ExecucaoEtapaResponse;
import br.com.smartprod.producao.dto.ExecucaoIngredienteResponse;
import br.com.smartprod.producao.dto.OrdemProducaoExecucaoResponse;

@SpringBootTest
@AutoConfigureMockMvc
class OrdemProducaoExecucaoSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrdemProducaoExecucaoService service;

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerentePodeAcessarExecucao() throws Exception {
        when(service.buscarExecucao(1L))
                .thenReturn(criarResposta());

        mockMvc.perform(get("/api/ordens-producao/1/execucao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordemProducaoId").value(1))
                .andExpect(jsonPath("$.nomeProduto").value("Pão francês"))
                .andExpect(jsonPath("$.ingredientes[0].quantidade").value(5.0))
                .andExpect(jsonPath("$.etapas[0].titulo").value("Mistura"));

        verify(service).buscarExecucao(1L);
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void funcionarioPodeAcessarExecucao() throws Exception {
        when(service.buscarExecucao(1L))
                .thenReturn(criarResposta());

        mockMvc.perform(get("/api/ordens-producao/1/execucao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordemProducaoId").value(1))
                .andExpect(jsonPath("$.fichaTecnicaId").value(3))
                .andExpect(jsonPath("$.ingredientes[0].nome").value("Farinha"))
                .andExpect(jsonPath("$.etapas[0].duracaoSegundos").value(300));

        verify(service).buscarExecucao(1L);
    }

    @Test
    void usuarioNaoAutenticadoRecebe401() throws Exception {
        mockMvc.perform(get("/api/ordens-producao/1/execucao"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OUTRO")
    void usuarioSemPermissaoRecebe403() throws Exception {
        mockMvc.perform(get("/api/ordens-producao/1/execucao"))
                .andExpect(status().isForbidden());
    }

    private OrdemProducaoExecucaoResponse criarResposta() {
        ExecucaoIngredienteResponse ingrediente =
                new ExecucaoIngredienteResponse(
                        5L,
                        "Farinha",
                        new BigDecimal("5.000000"),
                        UnidadeMedida.values()[0]);

        ExecucaoEtapaResponse etapa =
                new ExecucaoEtapaResponse(
                        1,
                        "Mistura",
                        "Misturar os ingredientes",
                        300);

        return new OrdemProducaoExecucaoResponse(
                1L,
                3L,
                "Pão francês",
                100,
                StatusOrdemProducao.PENDENTE,
                List.of(ingrediente),
                List.of(etapa));
    }
}
