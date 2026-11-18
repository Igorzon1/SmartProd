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
import br.com.smartprod.receita.dto.CalculoPorEstoqueRequest.EstoqueItem;
import br.com.smartprod.receita.dto.CalculoPorEstoqueResponse;
import br.com.smartprod.receita.dto.CalculoResponse;
import br.com.smartprod.receita.dto.IngredienteCalculadoResponse;

/** Contas da calculadora (RF006, RF007), sem banco. Pão francês: 1000 g de farinha, 700 ml de água, 20 g de sal, rende 50. */
@ExtendWith(MockitoExtension.class)
class CalculadoraServiceTest {

    @Mock
    private FichaTecnicaRepository fichaRepository;

    private CalculadoraService service;
    private FichaTecnica paoFrances;

    @BeforeEach
    void preparar() {
        service = new CalculadoraService(fichaRepository, new PorcentagemPadeiroService());
        paoFrances = new FichaTecnica("Pão francês", 50);
        ReflectionTestUtils.setField(paoFrances, "id", 1L);
        ingrediente(paoFrances, 1L, "Farinha de trigo", UnidadeMedida.G, "1000", true);
        ingrediente(paoFrances, 2L, "Água", UnidadeMedida.ML, "700", false);
        ingrediente(paoFrances, 3L, "Sal", UnidadeMedida.G, "20", false);
    }

    @Test
    void porDemandaMultiplicaPeloFatorEMostraAPorcentagemDoPadeiro() {
        when(fichaRepository.findById(1L)).thenReturn(Optional.of(paoFrances));

        CalculoResponse calculo = service.porDemanda(1L, 120);

        assertThat(calculo.fator()).isEqualByComparingTo("2.4");
        assertThat(calculo.ingredientes()).extracting(IngredienteCalculadoResponse::quantidade)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2400"), new BigDecimal("1680"), new BigDecimal("48"));
        assertThat(calculo.ingredientes()).extracting(IngredienteCalculadoResponse::percentualPadeiro)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100"), new BigDecimal("70"), new BigDecimal("2"));
    }

    @Test
    void quantidadeFicaComTresCasas() {
        FichaTecnica ficha = new FichaTecnica("Bolo", 3);
        ingrediente(ficha, 1L, "Farinha", UnidadeMedida.G, "100", true);
        when(fichaRepository.findById(7L)).thenReturn(Optional.of(ficha));

        assertThat(service.porDemanda(7L, 1).ingredientes().get(0).quantidade()).isEqualByComparingTo("33.333");
    }

    @Test
    void unidadesMenorQueUmDa400() {
        assertThatThrownBy(() -> service.porDemanda(1L, 0)).isInstanceOfSatisfying(ResponseStatusException.class,
                erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void porEstoqueAchaOIngredienteQueAcabaPrimeiro() {
        when(fichaRepository.findById(1L)).thenReturn(Optional.of(paoFrances));

        // farinha dá 250 pães, água dá 214 (3000 × 50 / 700 = 214,28), sal dá 250
        CalculoPorEstoqueResponse resultado = service.porEstoque(1L, List.of(
                estoque(1L, "5000"), estoque(2L, "3000"), estoque(3L, "100")));

        assertThat(resultado.unidadesPossiveis()).isEqualTo(214);
        assertThat(resultado.ingredienteLimitante().nome()).isEqualTo("Água");
        assertThat(resultado.ingredientes()).extracting(IngredienteCalculadoResponse::quantidade)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("4280"), new BigDecimal("2996"), new BigDecimal("85.6"));
    }

    @Test
    void ingredienteSemEstoqueInformadoContaComoZero() {
        when(fichaRepository.findById(1L)).thenReturn(Optional.of(paoFrances));

        CalculoPorEstoqueResponse resultado = service.porEstoque(1L, List.of(estoque(1L, "5000"), estoque(2L, "3000")));

        assertThat(resultado.unidadesPossiveis()).isZero();
        assertThat(resultado.ingredienteLimitante().nome()).isEqualTo("Sal");
    }

    @Test
    void insumoRepetidoNoEstoqueDa400() {
        assertThatThrownBy(() -> service.porEstoque(1L, List.of(estoque(1L, "1"), estoque(1L, "2"))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fichaSemIngredientesDa409EInexistenteDa404() {
        when(fichaRepository.findById(5L)).thenReturn(Optional.of(new FichaTecnica("Vazia", 10)));
        when(fichaRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.porEstoque(5L, List.of())).isInstanceOfSatisfying(ResponseStatusException.class,
                erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.porDemanda(9L, 10)).isInstanceOfSatisfying(ResponseStatusException.class,
                erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void ingrediente(FichaTecnica ficha, Long insumoId, String nome, UnidadeMedida unidade,
            String quantidade, boolean farinha) {
        Insumo insumo = new Insumo(nome, unidade, new BigDecimal("0.0100"));
        ReflectionTestUtils.setField(insumo, "id", insumoId);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, insumo, new BigDecimal(quantidade), farinha));
    }

    private static EstoqueItem estoque(Long insumoId, String quantidade) {
        return new EstoqueItem(insumoId, new BigDecimal(quantidade));
    }
}
