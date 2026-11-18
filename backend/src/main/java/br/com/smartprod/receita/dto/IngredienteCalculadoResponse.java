package br.com.smartprod.receita.dto;

import java.math.BigDecimal;

import br.com.smartprod.insumo.UnidadeMedida;

/** Ingrediente com a quantidade já ajustada ao lote (RF007). Sem custo: serve a Gerente e Funcionário. */
public record IngredienteCalculadoResponse(
        Long insumoId,
        String nome,
        UnidadeMedida unidadeMedida,
        BigDecimal quantidade,
        BigDecimal percentualPadeiro) {
}
