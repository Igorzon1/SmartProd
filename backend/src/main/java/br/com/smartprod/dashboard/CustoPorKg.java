package br.com.smartprod.dashboard;

import java.math.BigDecimal;
import java.util.List;

import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.receita.CustoDaFicha;

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
 * A conta fica em {@link CustoDaFicha}, a mesma que mostra o custo da ficha ao Gerente.
 */
final class CustoPorKg {

    private CustoPorKg() {
    }

    /** Um ingrediente da ficha, com a unidade e o custo do insumo. */
    record Ingrediente(BigDecimal quantidade, UnidadeMedida unidade, BigDecimal custoUnitario) {
    }

    /** @return custo de 1 kg em R$ (6 casas), ou {@code null} se a ficha não tem ingrediente pesável. */
    static BigDecimal calcular(List<Ingrediente> ingredientes) {
        return CustoDaFicha.custoPorKg(ingredientes.stream()
                .map(i -> new CustoDaFicha.Ingrediente(i.quantidade(), i.unidade(), i.custoUnitario()))
                .toList());
    }
}
