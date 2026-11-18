package br.com.smartprod.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.smartprod.insumo.UnidadeMedida;

class CustoPorKgTest {

    @Test
    void custoDaReceitaDivididoPeloPesoDaMassa() {
        // R$ 5,00 de farinha + R$ 1,00 de água = R$ 6,00; 1 kg + 1 L (= 1 kg) = 2 kg; R$ 3,00 por kg
        BigDecimal custo = CustoPorKg.calcular(List.of(
                ingrediente("1000", UnidadeMedida.G, "0.0050"),
                ingrediente("1000", UnidadeMedida.ML, "0.0010")));

        assertThat(custo).isEqualByComparingTo("3.00");
    }

    @Test
    void ingredienteEmUnidadeEntraNoCustoMasNaoNoPeso() {
        // R$ 5,00 de farinha (1 kg) + 2 ovos a R$ 0,80 = R$ 6,60 para 1 kg de massa
        BigDecimal custo = CustoPorKg.calcular(List.of(
                ingrediente("1000", UnidadeMedida.G, "0.0050"),
                ingrediente("2", UnidadeMedida.UN, "0.80")));

        assertThat(custo).isEqualByComparingTo("6.60");
    }

    @Test
    void quiloELitroJaEstaoEmKg() {
        BigDecimal custo = CustoPorKg.calcular(List.of(
                ingrediente("2", UnidadeMedida.KG, "6.50"),
                ingrediente("0.5", UnidadeMedida.L, "4.00")));

        assertThat(custo).isEqualByComparingTo("6.00"); // (13,00 + 2,00) / 2,5 kg
    }

    @Test
    void fichaSemIngredientePesavelNaoTemCustoPorKg() {
        assertThat(CustoPorKg.calcular(List.of(ingrediente("12", UnidadeMedida.UN, "0.80")))).isNull();
        assertThat(CustoPorKg.calcular(List.of())).isNull();
    }

    private static CustoPorKg.Ingrediente ingrediente(String quantidade, UnidadeMedida unidade, String custo) {
        return new CustoPorKg.Ingrediente(new BigDecimal(quantidade), unidade, new BigDecimal(custo));
    }
}
