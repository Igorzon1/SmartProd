package br.com.smartprod.receita.dto;

import java.util.List;

/** Quantas unidades dá para produzir com o estoque informado e qual ingrediente limita (RF007). */
public record CalculoPorEstoqueResponse(
        long unidadesPossiveis,
        IngredienteLimitante ingredienteLimitante,
        List<CalculoResponse.IngredienteCalculado> ingredientes) {

    public record IngredienteLimitante(Long insumoId, String nome) {
    }
}