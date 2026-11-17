package br.com.smartprod.producao;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import jakarta.persistence.EntityNotFoundException;

class OrdemProducaoExceptionHandlerTest {

    private final OrdemProducaoExceptionHandler handler = new OrdemProducaoExceptionHandler();

    @Test
    void deveRetornar409QuandoEstadoDaOrdemForInvalido() {
        IllegalStateException exception = new IllegalStateException(
                "Somente ordens pendentes podem ser iniciadas.");

        ProblemDetail resposta = handler.tratarEstadoInvalido(exception);

        assertThat(resposta.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(resposta.getDetail())
                .isEqualTo("Somente ordens pendentes podem ser iniciadas.");
    }

    @Test
    void deveRetornar400QuandoArgumentoForInvalido() {
        IllegalArgumentException exception = new IllegalArgumentException(
                "O responsável pela ordem deve ser um funcionário.");

        ProblemDetail resposta = handler.tratarArgumentoInvalido(exception);

        assertThat(resposta.getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());

        assertThat(resposta.getDetail())
                .isEqualTo("O responsável pela ordem deve ser um funcionário.");
    }

    @Test
    void deveRetornar404QuandoFichaTecnicaNaoExistir() {
        EntityNotFoundException exception = new EntityNotFoundException(
                "Ficha técnica não encontrada.");

        ProblemDetail resposta = handler.tratarNaoEncontrado(exception);

        assertThat(resposta.getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());

        assertThat(resposta.getDetail())
                .isEqualTo("Ficha técnica não encontrada.");
    }

}