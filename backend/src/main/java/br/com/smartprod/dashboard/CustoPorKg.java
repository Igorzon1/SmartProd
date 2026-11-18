package br.com.smartprod.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import br.com.smartprod.insumo.UnidadeMedida;

/**
 * Custo de 1 kg do produto de uma ficha técnica, para dar valor em R$ às perdas (RF013, RF014).
 *
 * <pre>
 * custo da receita = soma(quantidade × custo unitário)     (a quantidade já está na unidade do insumo)
 * peso da massa    = soma(quantidade em kg)                (g e ml ÷ 1000, kg e L direto, 1 ml = 1 g)
 * custo por kg     = custo da receita ÷ peso da massa
 * </pre>
 *
 * Ingrediente em {@code UN} entra no custo, mas não no peso (não dá para pesar "2 ovos"). Ficha sem
 * nenhum ingrediente pesável não tem custo por kg. Decisão de 2026-11-18 (Q7 para o custo das perdas).
 */
final class CustoPorKg {

    private static final BigDecimal MIL = BigDecimal.valueOf(1000);

    private CustoPorKg() {
    }

    /** Um ingrediente da ficha, com a unidade e o custo do insumo. */
    record Ingrediente(BigDecimal quantidade, UnidadeMedida unidade, BigDecimal custoUnitario) {
    }

    /** @return custo de 1 kg em R$ (6 casas), ou {@code null} se a ficha não tem ingrediente pesável. */
    static BigDecimal calcular(List<Ingrediente> ingredientes) {
        BigDecimal custo = BigDecimal.ZERO;
        BigDecimal pesoKg = BigDecimal.ZERO;
        for (Ingrediente ingrediente : ingredientes) {
            custo = custo.add(ingrediente.quantidade().multiply(ingrediente.custoUnitario()));
            pesoKg = pesoKg.add(emKg(ingrediente.quantidade(), ingrediente.unidade()));
        }
        if (pesoKg.signum() == 0) {
            return null;
        }
        return custo.divide(pesoKg, 6, RoundingMode.HALF_UP);
    }

    static BigDecimal emKg(BigDecimal quantidade, UnidadeMedida unidade) {
        return switch (unidade) {
            case G, ML -> quantidade.divide(MIL);
            case KG, L -> quantidade;
            case UN -> BigDecimal.ZERO;
        };
    }
}
