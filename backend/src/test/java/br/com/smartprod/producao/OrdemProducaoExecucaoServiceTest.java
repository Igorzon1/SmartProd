
package br.com.smartprod.producao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.producao.dto.OrdemProducaoExecucaoResponse;
import br.com.smartprod.receita.FichaIngrediente;
import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.PassoPreparo;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class OrdemProducaoExecucaoServiceTest {

    @Mock
    private OrdemProducaoService ordemProducaoService;

    @InjectMocks
    private OrdemProducaoExecucaoService service;

    @Test
    void deveRetornarIngredientesAjustadosEEtapasDaFicha() {
        FichaTecnica ficha = mock(FichaTecnica.class);
        FichaIngrediente ingrediente = mock(FichaIngrediente.class);
        Insumo insumo = mock(Insumo.class);
        PassoPreparo passo = mock(PassoPreparo.class);

        when(ficha.getId()).thenReturn(3L);
        when(ficha.getNome()).thenReturn("Pão francês");
        when(ficha.getRendimentoPadrao()).thenReturn(50);
        when(ficha.getIngredientes()).thenReturn(List.of(ingrediente));
        when(ficha.getPassos()).thenReturn(List.of(passo));

        when(ingrediente.getQuantidadeBase())
                .thenReturn(new BigDecimal("2.5"));
        when(ingrediente.getInsumo()).thenReturn(insumo);

        when(insumo.getId()).thenReturn(5L);
        when(insumo.getNome()).thenReturn("Farinha");
        when(insumo.getUnidadeMedida())
                .thenReturn(UnidadeMedida.values()[0]);

        when(passo.getOrdem()).thenReturn(1);
        when(passo.getEtapa()).thenReturn("Mistura");
        when(passo.getDescricao()).thenReturn("Misturar os ingredientes");
        when(passo.getDuracaoSegundos()).thenReturn(300);

        OrdemProducao ordem = criarOrdem(ficha, 100);

        when(ordemProducaoService.buscarPorId(1L))
                .thenReturn(ordem);

        OrdemProducaoExecucaoResponse resposta =
                service.buscarExecucao(1L);

        assertThat(resposta.ordemProducaoId()).isEqualTo(1L);
        assertThat(resposta.fichaTecnicaId()).isEqualTo(3L);
        assertThat(resposta.nomeProduto()).isEqualTo("Pão francês");
        assertThat(resposta.quantidadeUnidades()).isEqualTo(100);
        assertThat(resposta.status())
                .isEqualTo(StatusOrdemProducao.PENDENTE);

        assertThat(resposta.ingredientes()).hasSize(1);

        var ingredienteResposta = resposta.ingredientes().get(0);

        assertThat(ingredienteResposta.insumoId()).isEqualTo(5L);
        assertThat(ingredienteResposta.nome()).isEqualTo("Farinha");
        assertThat(ingredienteResposta.quantidade())
                .isEqualByComparingTo("5.0");
        assertThat(ingredienteResposta.unidade())
                .isEqualTo(UnidadeMedida.values()[0]);

        assertThat(resposta.etapas()).hasSize(1);

        var etapaResposta = resposta.etapas().get(0);

        assertThat(etapaResposta.ordem()).isEqualTo(1);
        assertThat(etapaResposta.titulo()).isEqualTo("Mistura");
        assertThat(etapaResposta.descricao())
                .isEqualTo("Misturar os ingredientes");
        assertThat(etapaResposta.duracaoSegundos()).isEqualTo(300);

        verify(ordemProducaoService).buscarPorId(1L);
    }

    @Test
    void deveRetornarErroQuandoOrdemNaoExistir() {
        when(ordemProducaoService.buscarPorId(99L))
                .thenThrow(new EntityNotFoundException(
                        "Ordem de produção não encontrada."));

        assertThatThrownBy(() -> service.buscarExecucao(99L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(ordemProducaoService).buscarPorId(99L);
    }

    @Test
    void deveRetornarConflitoQuandoOrdemNaoPossuirFicha() {
        OrdemProducao ordem = mock(OrdemProducao.class);

        when(ordemProducaoService.buscarPorId(1L))
                .thenReturn(ordem);

        assertThatThrownBy(() -> service.buscarExecucao(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException erro =
                            (ResponseStatusException) exception;

                    assertThat(erro.getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void deveRetornarConflitoQuandoRendimentoForInvalido() {
        FichaTecnica ficha = mock(FichaTecnica.class);

        when(ficha.getRendimentoPadrao()).thenReturn(0);

        OrdemProducao ordem = criarOrdem(ficha, 100);

        when(ordemProducaoService.buscarPorId(1L))
                .thenReturn(ordem);

        assertThatThrownBy(() -> service.buscarExecucao(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException erro =
                            (ResponseStatusException) exception;

                    assertThat(erro.getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                });
    }

    private OrdemProducao criarOrdem(
            FichaTecnica ficha,
            Integer quantidadeUnidades) {

        Usuario usuario = new Usuario(
                "Funcionário Teste",
                "funcionario@teste.com",
                "hash-teste",
                Perfil.FUNCIONARIO);

        OrdemProducao ordem = new OrdemProducao(
                ficha,
                quantidadeUnidades,
                Instant.parse("2026-11-20T12:00:00Z"),
                usuario,
                usuario);

        ReflectionTestUtils.setField(ordem, "id", 1L);

        return ordem;
    }
}
