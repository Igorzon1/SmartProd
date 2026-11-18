package br.com.smartprod.receita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import br.com.smartprod.insumo.InsumoRepository;
import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.receita.dto.FichaTecnicaRequest;
import br.com.smartprod.receita.dto.FichaTecnicaRequest.IngredienteRequest;
import br.com.smartprod.receita.dto.FichaTecnicaRequest.PassoRequest;
import br.com.smartprod.receita.dto.FichaTecnicaResponse;

/** Regras do cadastro e da edição de fichas (RF005), sem banco. */
@ExtendWith(MockitoExtension.class)
class FichaTecnicaEscritaServiceTest {

    @Mock
    private FichaTecnicaRepository fichaRepository;
    @Mock
    private InsumoRepository insumoRepository;

    private FichaTecnicaService service;
    private Insumo farinha;
    private Insumo agua;

    @BeforeEach
    void preparar() {
        service = new FichaTecnicaService(fichaRepository, new PorcentagemPadeiroService(), insumoRepository);
        farinha = insumo(1L, "Farinha de trigo", UnidadeMedida.G);
        agua = insumo(2L, "Água", UnidadeMedida.ML);
    }

    @Test
    void criaComNomeSemEspacosEPassosNaOrdemEPercentuais() {
        when(insumoRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(farinha, agua));

        FichaTecnicaResponse criada = service.criar(new FichaTecnicaRequest("  Pão francês ", 50,
                List.of(ingrediente(1L, "1000", true), ingrediente(2L, "700", false)),
                List.of(new PassoRequest(2, "Forno", "Asse.", 1200), new PassoRequest(1, "Mistura", "Misture.", null))));

        assertThat(criada.nome()).isEqualTo("Pão francês");
        assertThat(criada.ingredientes()).extracting(FichaTecnicaResponse.IngredienteResponse::percentualPadeiro)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100"), new BigDecimal("70"));
        assertThat(criada.passos()).extracting(FichaTecnicaResponse.PassoResponse::etapa).containsExactly("Mistura", "Forno");
        verify(fichaRepository).save(any(FichaTecnica.class));
    }

    @Test
    void insumoRepetidoNaMesmaFichaDa400() {
        assertThatThrownBy(() -> service.criar(new FichaTecnicaRequest("Pão", 10,
                List.of(ingrediente(1L, "100", true), ingrediente(1L, "50", false)), List.of())))
                .satisfies(erro -> badRequest(erro, "Cada insumo pode aparecer só uma vez na ficha."));
        verify(fichaRepository, never()).save(any());
    }

    @Test
    void insumoInexistenteDa400() {
        when(insumoRepository.findAllById(List.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.criar(new FichaTecnicaRequest("Pão", 10,
                List.of(ingrediente(99L, "100", true)), List.of())))
                .satisfies(erro -> badRequest(erro, "Insumo 99 não encontrado."));
    }

    @Test
    void insumoInativoNaoEntraEmFichaNova() {
        farinha.inativar();
        when(insumoRepository.findAllById(List.of(1L))).thenReturn(List.of(farinha));

        assertThatThrownBy(() -> service.criar(new FichaTecnicaRequest("Pão", 10,
                List.of(ingrediente(1L, "100", true)), List.of())))
                .satisfies(erro -> badRequest(erro, "O insumo \"Farinha de trigo\" está inativo e não pode entrar na ficha."));
    }

    @Test
    void naEdicaoOInsumoQueJaEstavaPodeTerSidoInativado() {
        FichaTecnica ficha = new FichaTecnica("Pão", 10);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, farinha, new BigDecimal("100"), true));
        farinha.inativar();
        when(fichaRepository.findById(5L)).thenReturn(Optional.of(ficha));
        when(insumoRepository.findAllById(List.of(1L))).thenReturn(List.of(farinha));

        FichaTecnicaResponse editada = service.atualizar(5L, new FichaTecnicaRequest("Pão de sal", 12,
                List.of(ingrediente(1L, "120", true)), List.of()));

        assertThat(editada.nome()).isEqualTo("Pão de sal");
        assertThat(editada.rendimentoPadrao()).isEqualTo(12);
        assertThat(editada.ingredientes()).singleElement()
                .satisfies(i -> assertThat(i.quantidadeBase()).isEqualByComparingTo("120"));
    }

    @Test
    void doisPassosComAMesmaOrdemDa400() {
        when(insumoRepository.findAllById(List.of(1L))).thenReturn(List.of(farinha));

        assertThatThrownBy(() -> service.criar(new FichaTecnicaRequest("Pão", 10, List.of(ingrediente(1L, "100", true)),
                List.of(new PassoRequest(1, "A", "a", null), new PassoRequest(1, "B", "b", null)))))
                .satisfies(erro -> badRequest(erro, "Dois passos não podem ter a mesma ordem."));
    }

    @Test
    void editarFichaInexistenteDa404() {
        when(fichaRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(9L, new FichaTecnicaRequest("Pão", 10,
                List.of(ingrediente(1L, "100", true)), List.of())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void badRequest(Throwable erro, String mensagem) {
        assertThat(erro).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getReason()).isEqualTo(mensagem);
        });
    }

    private static IngredienteRequest ingrediente(Long insumoId, String quantidade, boolean farinha) {
        return new IngredienteRequest(insumoId, new BigDecimal(quantidade), farinha);
    }

    private static Insumo insumo(Long id, String nome, UnidadeMedida unidade) {
        Insumo insumo = new Insumo(nome, unidade, new BigDecimal("0.0100"));
        ReflectionTestUtils.setField(insumo, "id", id);
        return insumo;
    }
}
