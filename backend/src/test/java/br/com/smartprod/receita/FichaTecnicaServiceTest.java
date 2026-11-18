package br.com.smartprod.receita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.receita.dto.FichaTecnicaResponse;
import br.com.smartprod.receita.dto.FichaTecnicaResumoResponse;
import br.com.smartprod.insumo.InsumoRepository;

/** Regras do {@link FichaTecnicaService}, sem banco e sem Spring. */
@ExtendWith(MockitoExtension.class)
class FichaTecnicaServiceTest {

    @Mock
    private FichaTecnicaRepository fichaTecnicaRepository;
    @Mock
    private InsumoRepository insumoRepository;

    private FichaTecnicaService fichaTecnicaService;

    @BeforeEach
    void preparar() {
        fichaTecnicaService = new FichaTecnicaService(fichaTecnicaRepository, new PorcentagemPadeiroService(), insumoRepository);
    }

    @Test
    void buscarLancaNaoEncontradoQuandoAFichaNaoExiste() {
        when(fichaTecnicaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fichaTecnicaService.buscar(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(erro -> assertThat(((ResponseStatusException) erro).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void buscarConverteAFichaParaResposta() {
        FichaTecnica ficha = new FichaTecnica("Pão francês", 50);
        when(fichaTecnicaRepository.findById(1L)).thenReturn(Optional.of(ficha));

        FichaTecnicaResponse resposta = fichaTecnicaService.buscar(1L);

        assertThat(resposta.nome()).isEqualTo("Pão francês");
        assertThat(resposta.rendimentoPadrao()).isEqualTo(50);
        assertThat(resposta.ingredientes()).isEmpty();
        assertThat(resposta.passos()).isEmpty();
    }

    @Test
    void listarConverteTodasAsFichas() {
        when(fichaTecnicaRepository.findAllByOrderByNomeAsc())
                .thenReturn(List.of(new FichaTecnica("Bolo", 10), new FichaTecnica("Pão", 50)));

        List<FichaTecnicaResumoResponse> resposta = fichaTecnicaService.listar();

        assertThat(resposta).extracting(FichaTecnicaResumoResponse::nome).containsExactly("Bolo", "Pão");
    }
}