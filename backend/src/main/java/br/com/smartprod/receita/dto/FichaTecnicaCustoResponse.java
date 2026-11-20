package br.com.smartprod.receita.dto;

import java.math.BigDecimal;

/**
 * Custo de uma ficha técnica, só para o Gerente (RNF-S002). {@code custoReceita} e {@code custoPorKg} em R$
 * com 2 casas; {@code custoUnidade} com 4, como o custo unitário dos insumos (um pão custa centavos).
 * {@code custoPorKg} é {@code null} quando a ficha não tem ingrediente pesável (só {@code UN}).
 */
public record FichaTecnicaCustoResponse(
        Long fichaId,
        String nome,
        int rendimentoPadrao,
        BigDecimal custoReceita,
        BigDecimal custoUnidade,
        BigDecimal custoPorKg) {
}
