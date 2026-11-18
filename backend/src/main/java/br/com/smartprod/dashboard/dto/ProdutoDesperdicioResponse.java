package br.com.smartprod.dashboard.dto;

import java.math.BigDecimal;

/** Item do ranking de desperdício (RF014). {@code totalReais} é {@code null} se a receita não tem custo por kg. */
public record ProdutoDesperdicioResponse(Long fichaId, String produto, BigDecimal totalKg, BigDecimal totalReais) {
}
