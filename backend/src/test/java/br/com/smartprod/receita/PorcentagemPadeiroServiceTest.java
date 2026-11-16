package br.com.smartprod.receita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Porcentagem do Padeiro (RF006), sem banco e sem Spring. */
class PorcentagemPadeiroServiceTest {

    private final PorcentagemPadeiroService servico = new PorcentagemPadeiroService();

    @Test
    void farinhaValeCemEAguaSetentaQuandoSaoMilEsetecentosGramas() {
        List<BigDecimal> resultado = servico.calcular(List.of(ingrediente("1000", true), ingrediente("700", false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), new BigDecimal("70.00"));
    }

    @Test
    void variasFarinhasJuntasSomamCem() {
        List<BigDecimal> resultado = servico.calcular(List.of(
                ingrediente("600", true), ingrediente("400", true), ingrediente("700", false)));

        assertThat(resultado).containsExactly(
                new BigDecimal("60.00"), new BigDecimal("40.00"), new BigDecimal("70.00"));
    }

    @Test
    void arredondaParaDuasCasasDecimais() {
        List<BigDecimal> resultado = servico.calcular(List.of(ingrediente("300", true), ingrediente("100", false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), new BigDecimal("33.33"));
    }

    @Test
    void ingredienteMaiorQueAFarinhaPassaDeCemPorCento() {
        List<BigDecimal> resultado = servico.calcular(List.of(ingrediente("500", true), ingrediente("750", false)));

        assertThat(resultado).containsExactly(new BigDecimal("100.00"), new BigDecimal("150.00"));
    }

    @Test
    void semIngredientesDevolveListaVazia() {
        assertThat(servico.calcular(List.of())).isEmpty();
    }

    @Test
    void semFarinhaLancaErroClaro() {
        assertThatThrownBy(() -> servico.calcular(List.of(ingrediente("700", false))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("farinha")
                .satisfies(erro -> assertThat(((ResponseStatusException) erro).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private static FichaIngrediente ingrediente(String quantidade, boolean farinha) {
        return new FichaIngrediente(null, null, new BigDecimal(quantidade), farinha);
    }
}