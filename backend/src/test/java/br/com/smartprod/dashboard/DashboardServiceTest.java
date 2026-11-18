package br.com.smartprod.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.dashboard.DashboardConsultas.IngredienteDaFicha;
import br.com.smartprod.dashboard.DashboardConsultas.PerdaPorFicha;
import br.com.smartprod.dashboard.DashboardConsultas.Rendimento;
import br.com.smartprod.dashboard.dto.ProdutoDesperdicioResponse;
import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.producao.StatusOrdemProducao;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 18);

    @Mock
    private DashboardConsultas consultas;

    private DashboardService service;

    @BeforeEach
    void criarServico() {
        // 18/11 às 02:00 UTC ainda é dia 17 em São Paulo (23:00)
        service = new DashboardService(consultas, Clock.fixed(Instant.parse("2026-11-18T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void hojeEOdiaDeSaoPauloENaoODeUtc() {
        assertThat(service.hoje()).isEqualTo(LocalDate.of(2026, 11, 17));
    }

    @Test
    void rendimentoComUmaCasaDecimal() {
        when(consultas.rendimento(any(), any())).thenReturn(new Rendimento(280, 118));

        assertThat(service.rendimento(DIA).rendimentoPercentual()).isEqualByComparingTo("42.1");
    }

    @Test
    void diaSemNadaPrevistoNaoTemPercentual() {
        when(consultas.rendimento(any(), any())).thenReturn(new Rendimento(0, 0));

        assertThat(service.rendimento(DIA).rendimentoPercentual()).isNull();
    }

    @Test
    void desperdicioSomaKgEReaisDeTodosOsProdutos() {
        perdas(new PerdaPorFicha(1L, "Pão francês", new BigDecimal("2.500")),
                new PerdaPorFicha(2L, "Brioche", new BigDecimal("1.000")));

        var desperdicio = service.desperdicio(DIA, DIA);

        assertThat(desperdicio.totalKg()).isEqualByComparingTo("3.5");
        assertThat(desperdicio.totalReais()).isEqualByComparingTo("25.00"); // 2,5 × 3,00 + 1 × 17,50
    }

    @Test
    void semPerdaOValorEZeroEOProdutoSemCustoPorKgFicaSemValor() {
        when(consultas.perdasPorFicha(any(), any())).thenReturn(List.of());
        assertThat(service.desperdicio(DIA, DIA).totalReais()).isEqualByComparingTo("0");

        when(consultas.perdasPorFicha(any(), any()))
                .thenReturn(List.of(new PerdaPorFicha(9L, "Ovos cozidos", BigDecimal.ONE)));
        when(consultas.ingredientes(any())).thenReturn(List.of(
                new IngredienteDaFicha(9L, new BigDecimal("12"), UnidadeMedida.UN, new BigDecimal("0.80"))));
        assertThat(service.desperdicio(DIA, DIA).totalReais()).isNull();
        assertThat(service.ranking(DIA, DIA)).extracting(ProdutoDesperdicioResponse::totalReais).containsOnlyNulls();
    }

    @Test
    void rankingPorKgComNoMaximoTresProdutos() {
        perdas(new PerdaPorFicha(1L, "Pão francês", new BigDecimal("2.500")),
                new PerdaPorFicha(2L, "Brioche", new BigDecimal("1.000")),
                new PerdaPorFicha(3L, "Baguete", new BigDecimal("4.000")),
                new PerdaPorFicha(4L, "Sonho", new BigDecimal("0.500")));

        assertThat(service.ranking(DIA, DIA)).extracting(ProdutoDesperdicioResponse::produto)
                .containsExactly("Baguete", "Pão francês", "Brioche");
    }

    @Test
    void periodoInvertidoDa400() {
        assertThatThrownBy(() -> service.desperdicio(DIA, DIA.minusDays(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void statusContaCadaSituacao() {
        when(consultas.contarOps(any(), any())).thenReturn(Map.of(StatusOrdemProducao.PENDENTE, 4L,
                StatusOrdemProducao.EM_ANDAMENTO, 2L, StatusOrdemProducao.CONCLUIDA, 9L));

        var status = service.statusOps();

        assertThat(status.pendentes()).isEqualTo(4);
        assertThat(status.emAndamento()).isEqualTo(2);
        assertThat(status.concluidas()).isEqualTo(9);
    }

    /** Pão francês custa R$ 3,00 por kg e Brioche R$ 17,50; as outras fichas usam só farinha a R$ 5,00/kg. */
    private void perdas(PerdaPorFicha... perdas) {
        when(consultas.perdasPorFicha(any(), any())).thenReturn(List.of(perdas));
        when(consultas.ingredientes(any())).thenReturn(List.of(
                new IngredienteDaFicha(1L, new BigDecimal("1000"), UnidadeMedida.G, new BigDecimal("0.0050")),
                new IngredienteDaFicha(1L, new BigDecimal("1000"), UnidadeMedida.ML, new BigDecimal("0.0010")),
                new IngredienteDaFicha(2L, new BigDecimal("500"), UnidadeMedida.G, new BigDecimal("0.0050")),
                new IngredienteDaFicha(2L, new BigDecimal("500"), UnidadeMedida.G, new BigDecimal("0.0300")),
                new IngredienteDaFicha(3L, new BigDecimal("1000"), UnidadeMedida.G, new BigDecimal("0.0050")),
                new IngredienteDaFicha(4L, new BigDecimal("1000"), UnidadeMedida.G, new BigDecimal("0.0050"))));
    }
}
