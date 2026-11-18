package br.com.smartprod.producao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;
import br.com.smartprod.shared.ConflitoException;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;

/** Regra do cancelamento de OP: só a pendente pode sair da esteira. */
@ExtendWith(MockitoExtension.class)
class OrdemProducaoCancelamentoTest {

    @Mock
    private OrdemProducaoRepository repository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private FichaTecnicaRepository fichaTecnicaRepository;

    @InjectMocks
    private OrdemProducaoService service;

    @Test
    void ordemPendentePodeSerCancelada() {
        assertThatCode(() -> novaOrdem().validarCancelamento()).doesNotThrowAnyException();
    }

    @Test
    void ordemEmAndamentoNaoPodeSerCancelada() {
        OrdemProducao ordem = novaOrdem();
        ordem.iniciar();

        assertThatThrownBy(ordem::validarCancelamento)
                .isInstanceOf(ConflitoException.class)
                .hasMessageStartingWith("Só dá para cancelar uma OP pendente.");
    }

    @Test
    void ordemConcluidaNaoPodeSerCancelada() {
        OrdemProducao ordem = novaOrdem();
        ordem.iniciar();
        ordem.concluir(100);

        assertThatThrownBy(ordem::validarCancelamento).isInstanceOf(ConflitoException.class);
    }

    @Test
    void servicoExcluiAOrdemPendente() {
        OrdemProducao ordem = novaOrdem();
        when(repository.findById(10L)).thenReturn(Optional.of(ordem));

        service.cancelar(10L);

        verify(repository).delete(ordem);
    }

    @Test
    void servicoNaoExcluiOrdemQueJaComecou() {
        OrdemProducao ordem = novaOrdem();
        ordem.iniciar();
        when(repository.findById(10L)).thenReturn(Optional.of(ordem));

        assertThatThrownBy(() -> service.cancelar(10L)).isInstanceOf(ConflitoException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void servicoAvisaQuandoAOrdemNaoExiste() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Ordem de produção não encontrada.");

        verify(repository, never()).delete(any());
    }

    private static OrdemProducao novaOrdem() {
        Usuario gerente = new Usuario("Gerente", "gerente@padaria.com", "hash", Perfil.GERENTE);
        return new OrdemProducao(new FichaTecnica("Pão francês", 50), 100,
                Instant.parse("2026-11-20T14:00:00Z"), gerente, gerente);
    }
}
