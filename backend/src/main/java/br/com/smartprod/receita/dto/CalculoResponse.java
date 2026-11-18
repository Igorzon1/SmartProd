package br.com.smartprod.receita.dto;

import java.math.BigDecimal;
import java.util.List;

/** Resposta de {@code GET /api/fichas-tecnicas/{id}/calculo?unidades=N} (RF007, por demanda). */
public record CalculoResponse(Long fichaId, int unidades, BigDecimal fator, List<IngredienteCalculadoResponse> ingredientes) {
}
