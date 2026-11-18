package br.com.smartprod.receita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.receita.dto.CalculoPorEstoqueRequest;
import br.com.smartprod.receita.dto.CalculoPorEstoqueRequest.EstoqueItem;
import br.com.smartprod.receita.dto.CalculoPorEstoqueResponse;
import br.com.smartprod.receita.dto.CalculoResponse;

/** Calculadora da ficha técnica (RF007), sem banco e sem Spring. */
@ExtendWith(MockitoExtension.class)
class CalculoFichaServiceTest {

    private static final Long FARINHA = 1L;
    private static final Long AGUA = 2L;

    @Mock
    private FichaTecnicaRepository fichaTecnicaRepository;

    private CalculoFichaService servico;

    @BeforeEach
    void preparar() {
        servico = new CalculoFichaService(fichaTecnicaRepository, new PorcentagemPadeiroService());
    }

    // ---- por demanda

    @Test
    void recalculaOsIngredientesPelaProporcao() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        CalculoResponse resposta = servico.calcular(1L, 120);

        assertThat(resposta.fichaId()).isEqualTo(1L);
        assertThat(resposta.unidades()).isEqualTo(120);
        assertThat(resposta.fator()).isEqualByComparingTo("2.4");
        assertThat(resposta.ingredientes()).hasSize(2);
        assertThat(resposta.ingredientes().get(0).quantidade()).isEqualByComparingTo("2400");
        assertThat(resposta.ingredientes().get(0).percentualPadeiro()).isEqualByComparingTo("100");
        assertThat(resposta.ingredientes().get(1).quantidade()).isEqualByComparingTo("1680");
        assertThat(resposta.ingredientes().get(1).percentualPadeiro()).isEqualByComparingTo("70");
    }

    @Test
    void mesmaQuantidadeDoRendimentoMantemAsQuantidadesBase() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        CalculoResponse resposta = servico.calcular(1L, 50);

        assertThat(resposta.fator()).isEqualByComparingTo("1");
        assertThat(resposta.ingredientes().get(0).quantidade()).isEqualByComparingTo("1000");
        assertThat(resposta.ingredientes().get(1).quantidade()).isEqualByComparingTo("700");
    }

    @Test
    void arredondaAsQuantidadesParaQuatroCasasDecimais() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(3, "100", "70")));

        CalculoResponse resposta = servico.calcular(1L, 1);

        assertThat(resposta.fator()).isEqualByComparingTo("0.3333");
        assertThat(resposta.ingredientes().get(0).quantidade()).isEqualByComparingTo("33.3333");
        assertThat(resposta.ingredientes().get(1).quantidade()).isEqualByComparingTo("23.3333");
    }

    @Test
    void unidadesZeroOuNegativasSaoRejeitadas() {
        assertThatThrownBy(() -> servico.calcular(1L, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> servico.calcular(1L, -5))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fichaInexistenteDevolve404NosDoisCalculos() {
        when(fichaTecnicaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.calcular(99L, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> servico.calcularPorEstoque(99L, estoque()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- por estoque

    @Test
    void calculaAsUnidadesPossiveisEOIngredienteLimitante() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        CalculoPorEstoqueResponse resposta = servico.calcularPorEstoque(1L,
                estoque(item(FARINHA, "5000"), item(AGUA, "10000")));

        assertThat(resposta.unidadesPossiveis()).isEqualTo(250);
        assertThat(resposta.ingredienteLimitante().insumoId()).isEqualTo(FARINHA);
        assertThat(resposta.ingredienteLimitante().nome()).isEqualTo("Farinha de trigo");
        assertThat(resposta.ingredientes().get(0).quantidade()).isEqualByComparingTo("5000");
        assertThat(resposta.ingredientes().get(1).quantidade()).isEqualByComparingTo("3500");
    }

    @Test
    void oIngredienteComMenosEstoqueEOLimitante() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        CalculoPorEstoqueResponse resposta = servico.calcularPorEstoque(1L,
                estoque(item(FARINHA, "5000"), item(AGUA, "1000")));

        assertThat(resposta.unidadesPossiveis()).isEqualTo(71);
        assertThat(resposta.ingredienteLimitante().insumoId()).isEqualTo(AGUA);
        assertThat(resposta.ingredientes().get(0).quantidade()).isEqualByComparingTo("1420");
        assertThat(resposta.ingredientes().get(1).quantidade()).isEqualByComparingTo("994");
    }

    @Test
    void estoqueZeroResultaEmZeroUnidades() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        CalculoPorEstoqueResponse resposta = servico.calcularPorEstoque(1L,
                estoque(item(FARINHA, "0"), item(AGUA, "10000")));

        assertThat(resposta.unidadesPossiveis()).isZero();
        assertThat(resposta.ingredienteLimitante().insumoId()).isEqualTo(FARINHA);
        assertThat(resposta.ingredientes().get(0).quantidade()).isEqualByComparingTo("0");
    }

    @Test
    void insumoFaltandoNoEstoqueContaComoZero() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        CalculoPorEstoqueResponse resposta = servico.calcularPorEstoque(1L, estoque(item(FARINHA, "5000")));

        assertThat(resposta.unidadesPossiveis()).isZero();
        assertThat(resposta.ingredienteLimitante().insumoId()).isEqualTo(AGUA);
    }

    @Test
    void estoqueVazioResultaEmZeroUnidades() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        CalculoPorEstoqueResponse resposta = servico.calcularPorEstoque(1L, estoque());

        assertThat(resposta.unidadesPossiveis()).isZero();
        assertThat(resposta.ingredienteLimitante().insumoId()).isEqualTo(FARINHA);
    }

    @Test
    void asUnidadesPossiveisSaoArredondadasParaBaixo() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        // 1999 g de farinha dariam 99,95 unidades: só 99 inteiras
        CalculoPorEstoqueResponse resposta = servico.calcularPorEstoque(1L,
                estoque(item(FARINHA, "1999"), item(AGUA, "10000")));

        assertThat(resposta.unidadesPossiveis()).isEqualTo(99);
        assertThat(resposta.ingredienteLimitante().insumoId()).isEqualTo(FARINHA);
    }

    @Test
    void estoqueDoMesmoInsumoEmVariasLinhasSeSoma() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        CalculoPorEstoqueResponse resposta = servico.calcularPorEstoque(1L,
                estoque(item(FARINHA, "2000"), item(FARINHA, "3000"), item(AGUA, "10000")));

        assertThat(resposta.unidadesPossiveis()).isEqualTo(250);
    }

    @Test
    void estoqueDeInsumoForaDaFichaEIgnorado() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha(50, "1000", "700")));

        CalculoPorEstoqueResponse resposta = servico.calcularPorEstoque(1L,
                estoque(item(FARINHA, "5000"), item(AGUA, "10000"), item(99L, "1")));

        assertThat(resposta.unidadesPossiveis()).isEqualTo(250);
    }

    @Test
    void fichaSemIngredientesNaoPodeSerCalculadaPorEstoque() {
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(new FichaTecnica("Vazia", 10)));

        assertThatThrownBy(() -> servico.calcularPorEstoque(1L, estoque(item(FARINHA, "5000"))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ---- apoio

    private static FichaTecnica ficha(int rendimento, String quantidadeFarinha, String quantidadeAgua) {
        FichaTecnica ficha = new FichaTecnica("Pão francês", rendimento);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha,
                insumo(FARINHA, "Farinha de trigo", UnidadeMedida.G), new BigDecimal(quantidadeFarinha), true));
        ficha.adicionarIngrediente(new FichaIngrediente(ficha,
                insumo(AGUA, "Água", UnidadeMedida.ML), new BigDecimal(quantidadeAgua), false));
        return ficha;
    }

    private static Insumo insumo(Long id, String nome, UnidadeMedida unidade) {
        Insumo insumo = new Insumo(nome, unidade, new BigDecimal("1.00"));
        ReflectionTestUtils.setField(insumo, "id", id);
        return insumo;
    }

    private static EstoqueItem item(Long insumoId, String quantidade) {
        return new EstoqueItem(insumoId, new BigDecimal(quantidade));
    }

    private static CalculoPorEstoqueRequest estoque(EstoqueItem... itens) {
        return new CalculoPorEstoqueRequest(List.of(itens));
    }
}