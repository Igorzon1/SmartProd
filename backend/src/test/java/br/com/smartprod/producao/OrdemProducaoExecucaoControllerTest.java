
package br.com.smartprod.producao;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.producao.dto.ExecucaoEtapaResponse;
import br.com.smartprod.producao.dto.ExecucaoIngredienteResponse;
import br.com.smartprod.producao.dto.OrdemProducaoExecucaoResponse;
import jakarta.persistence.EntityNotFoundException;
import br.com.smartprod.shared.TratadorDeErros;

@ExtendWith(MockitoExtension.class)
class OrdemProducaoExecucaoControllerTest {

    @Mock
    private OrdemProducaoExecucaoService service;

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrdemProducaoExecucaoController(service))
                .setControllerAdvice(new TratadorDeErros())
                .build();
    }

    @Test
    void deveRetornar200ComIngredientesEEtapas() throws Exception {
        ExecucaoIngredienteResponse ingrediente = new ExecucaoIngredienteResponse(
                5L,
                "Farinha",
                new BigDecimal("5.000000"),
                UnidadeMedida.values()[0]);

        ExecucaoEtapaResponse etapa = new ExecucaoEtapaResponse(
                1,
                "Mistura",
                "Misturar os ingredientes",
                300);

        OrdemProducaoExecucaoResponse resposta = new OrdemProducaoExecucaoResponse(
                1L,
                3L,
                "Pão francês",
                100,
                StatusOrdemProducao.PENDENTE,
                List.of(ingrediente),
                List.of(etapa));

        when(service.buscarExecucao(1L))
                .thenReturn(resposta);

        mockMvc.perform(get("/api/ordens-producao/1/execucao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordemProducaoId").value(1))
                .andExpect(jsonPath("$.fichaTecnicaId").value(3))
                .andExpect(jsonPath("$.fichaNome").value("Pão francês"))
                .andExpect(jsonPath("$.quantidadeUnidades").value(100))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.ingredientes[0].insumoId").value(5))
                .andExpect(jsonPath("$.ingredientes[0].nome").value("Farinha"))
                .andExpect(jsonPath("$.ingredientes[0].quantidade").value(5.0))
                .andExpect(jsonPath("$.passos[0].ordem").value(1))
                .andExpect(jsonPath("$.passos[0].etapa").value("Mistura"))
                .andExpect(jsonPath("$.passos[0].descricao")
                        .value("Misturar os ingredientes"))
                .andExpect(jsonPath("$.passos[0].duracaoSegundos").value(300));

        verify(service).buscarExecucao(1L);
    }

    @Test
    void deveRetornar404QuandoOrdemNaoExistir() throws Exception {
        when(service.buscarExecucao(99L))
                .thenThrow(new EntityNotFoundException(
                        "Ordem de produção não encontrada."));

        mockMvc.perform(get("/api/ordens-producao/99/execucao"))
                .andExpect(status().isNotFound());

        verify(service).buscarExecucao(99L);
    }

    @Test
    void deveRetornar409QuandoOrdemNaoPossuirFicha() throws Exception {
        when(service.buscarExecucao(1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A ordem de produção não possui ficha técnica associada."));

        mockMvc.perform(get("/api/ordens-producao/1/execucao"))
                .andExpect(status().isConflict());

        verify(service).buscarExecucao(1L);
    }
}
