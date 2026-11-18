package br.com.smartprod.receita.dto;

import java.math.BigDecimal;
import java.util.List;

import br.com.smartprod.insumo.UnidadeMedida;

/** Receita recalculada para uma quantidade de unidades (RF007). Sem custos (RNF-S002). */
public record CalculoResponse(
        Long fichaId,
        int unidades,
        BigDecimal fator,
        List<IngredienteCalculado> ingredientes) {

    public record IngredienteCalculado(
            Long insumoId,
            String nome,
            UnidadeMedida unidadeMedida,
            BigDecimal quantidade,
            BigDecimal percentualPadeiro) {
    }
}