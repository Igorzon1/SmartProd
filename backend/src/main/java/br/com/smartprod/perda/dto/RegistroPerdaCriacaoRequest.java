
package br.com.smartprod.perda.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistroPerdaCriacaoRequest(

        @NotBlank @Size(max = 120) String etapa,

        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal pesoKg,

        @Positive Integer unidades,

        @NotBlank @Size(max = 500) String motivo) {
}
