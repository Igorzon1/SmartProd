package br.com.smartprod.receita;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.receita.CustoDaFicha.Ingrediente;

/** Custo da ficha técnica (docs/07): receita, unidade e kg. */
class CustoDaFichaTest {

    /** Pão francês do teste de usabilidade: 5 kg de farinha rendem 135 pães. */
    private static final List<Ingrediente> PAO_FRANCES = List.of(
            new Ingrediente(new BigDecimal("5000"), UnidadeMedida.G, new BigDecimal("0.0030")),
            new Ingrediente(new BigDecimal("3000"), UnidadeMedida.ML, BigDecimal.ZERO),
            new Ingrediente(new BigDecimal("100"), UnidadeMedida.G, new BigDecimal("0.0030")),
            new Ingrediente(new BigDecimal("150"), UnidadeMedida.G, new BigDecimal("0.0200")),
            new Ingrediente(new BigDecimal("50"), UnidadeMedida.G, new BigDecimal("0.0045")),
            new Ingrediente(new BigDecimal("50"), UnidadeMedida.G, new BigDecimal("0.0130")),
            new Ingrediente(new BigDecimal("50"), UnidadeMedida.G, new BigDecimal("0.0150")));

    @Test
    void custoDaReceitaSomaQuantidadeVezesCustoUnitario() {
        assertThat(CustoDaFicha.custoReceita(PAO_FRANCES)).isEqualByComparingTo("19.925");
    }

    @Test
    void custoDaUnidadeDivideOCustoPeloRendimentoComQuatroCasas() {
        assertThat(CustoDaFicha.custoUnidade(PAO_FRANCES, 135)).isEqualByComparingTo("0.1476");
    }

    @Test
    void custoPorKgDivideOCustoPeloPesoDaMassa() {
        // 8,4 kg de massa (água conta 1 ml = 1 g)
        assertThat(CustoDaFicha.custoPorKg(PAO_FRANCES)).isEqualByComparingTo("2.372024");
    }

    @Test
    void ingredienteEmKgOuLitroEntraDiretoNoPeso() {
        List<Ingrediente> emQuilos = List.of(
                new Ingrediente(new BigDecimal("5"), UnidadeMedida.KG, new BigDecimal("2.9674")),
                new Ingrediente(new BigDecimal("3"), UnidadeMedida.L, BigDecimal.ZERO));

        assertThat(CustoDaFicha.custoPorKg(emQuilos)).isEqualByComparingTo("1.854625");
    }

    @Test
    void ingredienteEmUnidadeEntraNoCustoMasNaoNoPeso() {
        List<Ingrediente> comOvos = List.of(
                new Ingrediente(new BigDecimal("1000"), UnidadeMedida.G, new BigDecimal("0.0050")),
                new Ingrediente(new BigDecimal("4"), UnidadeMedida.UN, new BigDecimal("0.8000")));

        assertThat(CustoDaFicha.custoReceita(comOvos)).isEqualByComparingTo("8.2");
        assertThat(CustoDaFicha.custoPorKg(comOvos)).isEqualByComparingTo("8.2");
    }

    @Test
    void fichaSemIngredientePesavelNaoTemCustoPorKg() {
        List<Ingrediente> soOvos = List.of(new Ingrediente(new BigDecimal("6"), UnidadeMedida.UN, new BigDecimal("0.8")));

        assertThat(CustoDaFicha.custoPorKg(soOvos)).isNull();
        assertThat(CustoDaFicha.custoReceita(soOvos)).isEqualByComparingTo("4.8");
    }

    @Test
    void fichaSemIngredientesCustaZero() {
        assertThat(CustoDaFicha.custoReceita(List.of())).isEqualByComparingTo("0");
        assertThat(CustoDaFicha.custoUnidade(List.of(), 10)).isEqualByComparingTo("0");
        assertThat(CustoDaFicha.custoPorKg(List.of())).isNull();
    }
}
