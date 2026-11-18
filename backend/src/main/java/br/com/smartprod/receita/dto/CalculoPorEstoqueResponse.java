package br.com.smartprod.receita.dto;

import java.util.List;

/**
 * Resposta de {@code POST /api/fichas-tecnicas/{id}/calculo-por-estoque} (RF007): quantas unidades o estoque
 * permite, qual ingrediente limita e quanto usar de cada um para essas unidades.
 */
public record CalculoPorEstoqueResponse(
        Long fichaId,
        long unidadesPossiveis,
        IngredienteLimitante ingredienteLimitante,
        List<IngredienteCalculadoResponse> ingredientes) {

    public record IngredienteLimitante(Long insumoId, String nome) {
    }
}
