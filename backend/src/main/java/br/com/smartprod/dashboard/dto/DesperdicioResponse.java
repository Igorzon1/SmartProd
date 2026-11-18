package br.com.smartprod.dashboard.dto;

import java.math.BigDecimal;

/**
 * Desperdício no período (RF013): peso perdido em kg e o valor em R$ pelo custo por kg de cada receita.
 * {@code totalReais} é {@code null} só se houve perda e nenhuma das receitas tem custo por kg calculável.
 */
public record DesperdicioResponse(BigDecimal totalKg, BigDecimal totalReais) {
}
