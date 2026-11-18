package br.com.smartprod.receita.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo de {@code POST /api/fichas-tecnicas/{id}/calculo-por-estoque} (RF007). A quantidade em estoque fica
 * na unidade de medida do insumo, a mesma da ficha. Ingrediente que não aparece na lista conta como zero.
 */
public record CalculoPorEstoqueRequest(@NotNull @Valid List<EstoqueItem> estoque) {

    public record EstoqueItem(
            @NotNull Long insumoId,
            @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal quantidade) {
    }
}
