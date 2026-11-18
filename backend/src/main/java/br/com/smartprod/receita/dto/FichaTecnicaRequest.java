package br.com.smartprod.receita.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/fichas-tecnicas} e {@code PUT /api/fichas-tecnicas/{id}} (RF005).
 * A quantidade de cada ingrediente fica na unidade de medida do próprio insumo (docs/08, Q7).
 */
public record FichaTecnicaRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotNull @Positive Integer rendimentoPadrao,
        @NotEmpty @Valid List<IngredienteRequest> ingredientes,
        @NotNull @Valid List<PassoRequest> passos) {

    public record IngredienteRequest(
            @NotNull Long insumoId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 4) BigDecimal quantidadeBase,
            boolean ehFarinha) {
    }

    /** {@code duracaoSegundos} é opcional; quando vem, vira temporizador no passo a passo (RF010). */
    public record PassoRequest(
            @NotNull @Positive Integer ordem,
            @NotBlank @Size(max = 120) String etapa,
            @NotBlank @Size(max = 1000) String descricao,
            @Positive Integer duracaoSegundos) {
    }
}
