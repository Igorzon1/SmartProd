package br.com.smartprod.receita;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import br.com.smartprod.insumo.UnidadeMedida;

/**
 * Custo de uma ficha técnica (docs/07, decisão de 2026-11-18 sobre a Q7). Só o Gerente vê esses valores.
 *
 * <pre>
 * custo da receita = soma(quantidade × custo unitário)     (a quantidade já está na unidade do insumo)
 * custo por unidade = custo da receita ÷ rendimento padrão
 * peso da massa    = soma(quantidade em kg)                (g e ml ÷ 1000, kg e L direto, 1 ml = 1 g)
 * custo por kg     = custo da receita ÷ peso da massa
 * </pre>
 *
 * Ingrediente em {@code UN} entra no custo, mas não no peso (não dá para pesar "2 ovos"): ficha sem
 * ingrediente pesável não tem custo por kg. O dashboard usa a mesma conta para dar valor às perdas.
 */
public final class CustoDaFicha {

    private static final BigDecimal MIL = BigDecimal.valueOf(1000);

    private CustoDaFicha() {
    }

    /** Um ingrediente da ficha, com a unidade e o custo do insumo. */
    public record Ingrediente(BigDecimal quantidade, UnidadeMedida unidade, BigDecimal custoUnitario) {
    }

    /** Custo da receita inteira em R$, sem arredondar. */
    public static BigDecimal custoReceita(List<Ingrediente> ingredientes) {
        return ingredientes.stream()
                .map(ingrediente -> ingrediente.quantidade().multiply(ingrediente.custoUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** @return custo de 1 kg em R$ (6 casas), ou {@code null} se a ficha não tem ingrediente pesável. */
    public static BigDecimal custoPorKg(List<Ingrediente> ingredientes) {
        BigDecimal pesoKg = ingredientes.stream()
                .map(ingrediente -> emKg(ingrediente.quantidade(), ingrediente.unidade()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pesoKg.signum() == 0) {
            return null;
        }
        return custoReceita(ingredientes).divide(pesoKg, 6, RoundingMode.HALF_UP);
    }

    /** Custo de uma unidade produzida em R$, com 4 casas (como o custo unitário dos insumos). */
    public static BigDecimal custoUnidade(List<Ingrediente> ingredientes, int rendimentoPadrao) {
        return custoReceita(ingredientes).divide(BigDecimal.valueOf(rendimentoPadrao), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal emKg(BigDecimal quantidade, UnidadeMedida unidade) {
        return switch (unidade) {
            case G, ML -> quantidade.divide(MIL);
            case KG, L -> quantidade;
            case UN -> BigDecimal.ZERO;
        };
    }
}
