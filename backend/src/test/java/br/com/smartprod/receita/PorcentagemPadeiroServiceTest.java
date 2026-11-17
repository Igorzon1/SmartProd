package br.com.smartprod.receita;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.UnidadeMedida;

/** Porcentagem do Padeiro (RF006), sem banco e sem Spring. */
class PorcentagemPadeiroServiceTest {

    private final PorcentagemPadeiroService servico = new PorcentagemPadeiroService();

    @Test
    void farinhaValeCemEAguaSetentaQuandoSaoMilEsetecentosGramas() {
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("1000", UnidadeMedida.G, true), ingrediente("700", UnidadeMedida.G, false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), new BigDecimal("70.00"));
    }

    @Test
    void variasFarinhasJuntasSomamCem() {
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("600", UnidadeMedida.G, true),
                ingrediente("400", UnidadeMedida.G, true),
                ingrediente("700", UnidadeMedida.G, false)));

        assertThat(resultado).containsExactly(
                new BigDecimal("60.00"), new BigDecimal("40.00"), new BigDecimal("70.00"));
    }

    @Test
    void arredondaParaDuasCasasDecimais() {
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("300", UnidadeMedida.G, true), ingrediente("100", UnidadeMedida.G, false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), new BigDecimal("33.33"));
    }

    @Test
    void ingredienteMaiorQueAFarinhaPassaDeCemPorCento() {
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("500", UnidadeMedida.G, true), ingrediente("750", UnidadeMedida.G, false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), new BigDecimal("150.00"));
    }

    @Test
    void semIngredientesDevolveListaVazia() {
        assertThat(servico.calcular(List.of())).isEmpty();
    }

    // ---- unidades (docs/08, Q7)

    @Test
    void converteKgParaGramasAntesDeComparar() {
        // 5 kg de farinha e 100 g de sal: o sal é 2%, e não 2000%.
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("5", UnidadeMedida.KG, true), ingrediente("100", UnidadeMedida.G, false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), new BigDecimal("2.00"));
    }

    @Test
    void converteLitrosParaMililitrosEComparaComAFarinhaEmGramas() {
        // 1 kg de farinha e 0,7 L de água: 700 ml contam como 700 g, ou seja, 70%.
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("1", UnidadeMedida.KG, true), ingrediente("0.7", UnidadeMedida.L, false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), new BigDecimal("70.00"));
    }

    @Test
    void aguaEmMililitrosContaComoGramas() {
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("1000", UnidadeMedida.G, true), ingrediente("650", UnidadeMedida.ML, false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), new BigDecimal("65.00"));
    }

    @Test
    void ingredienteEmUnidadeFicaSemPercentualEOsOutrosContinuam() {
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("1000", UnidadeMedida.G, true),
                ingrediente("2", UnidadeMedida.UN, false),
                ingrediente("20", UnidadeMedida.G, false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), null, new BigDecimal("2.00"));
    }

    // ---- ficha sem farinha: não é erro, só não há percentual

    @Test
    void semFarinhaDevolveNuloParaTodosEmVezDeErro() {
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("395", UnidadeMedida.G, false), ingrediente("200", UnidadeMedida.G, false)));

        assertThat(resultado).hasSize(2).containsOnlyNulls();
    }

    @Test
    void farinhaEmUnidadeNaoServeDeBase() {
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("2", UnidadeMedida.UN, true), ingrediente("100", UnidadeMedida.G, false)));

        assertThat(resultado).isEqualTo(Arrays.asList(null, null));
    }

    private static FichaIngrediente ingrediente(String quantidade, UnidadeMedida unidade, boolean farinha) {
        Insumo insumo = new Insumo("Insumo", unidade, BigDecimal.ONE);
        return new FichaIngrediente(null, insumo, new BigDecimal(quantidade), farinha);
    }
}
