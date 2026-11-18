package br.com.smartprod.dashboard.dto;

import java.math.BigDecimal;

/**
 * Rendimento do dia (RF012): unidades previstas nas OPs com entrega no dia e unidades produzidas nas que já
 * foram concluídas. {@code rendimentoPercentual} é {@code null} quando não há nada previsto.
 */
public record RendimentoResponse(long previsto, long produzido, BigDecimal rendimentoPercentual) {
}
