package br.com.smartprod.receita.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** Estoque disponível por insumo, enviado pelo cliente (não existe cadastro de estoque, questão Q2). */
public record CalculoPorEstoqueRequest(@NotNull List<@NotNull @Valid EstoqueItem> estoque) {

    public record EstoqueItem(
            @NotNull Long insumoId,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 4) BigDecimal quantidade) {
    }
}