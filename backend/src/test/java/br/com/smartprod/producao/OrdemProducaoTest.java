
package br.com.smartprod.producao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;

class OrdemProducaoTest {

    @Test
    void naoDeveCriarOrdemComQuantidadeZero() {
        assertThatThrownBy(() -> new OrdemProducao(
                new FichaTecnica("Pão francês", 50),
                0,
                java.time.Instant.now(),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quantidade de unidades deve ser maior que zero.");
    }

    @Test
    void naoDeveCriarOrdemComQuantidadeNegativa() {
        assertThatThrownBy(() -> new OrdemProducao(
                new FichaTecnica("Pão francês", 50),
                -5,
                java.time.Instant.now(),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quantidade de unidades deve ser maior que zero.");
    }

    @Test
    void naoDeveCriarOrdemSemHorarioEntrega() {
        assertThatThrownBy(() -> new OrdemProducao(
                new FichaTecnica("Pão francês", 50),
                10,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O horário de entrega é obrigatório.");
    }

    @Test
    void naoDeveCriarOrdemSemUsuarios() {
        assertThatThrownBy(() -> new OrdemProducao(
                new FichaTecnica("Pão francês", 50),
                10,
                java.time.Instant.now(),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O responsável e o criador da ordem são obrigatórios.");
    }

    @Test
    void deveCriarOrdemComFichaTecnica() {
        Usuario usuario = criarUsuario();
        FichaTecnica ficha = new FichaTecnica("Pão francês", 50);

        OrdemProducao ordem = new OrdemProducao(
                ficha,
                100,
                java.time.Instant.now(),
                usuario,
                usuario);

        assertThat(ordem.getFichaTecnica()).isSameAs(ficha);
        assertThat(ordem.getQuantidadeUnidades()).isEqualTo(100);
        assertThat(ordem.getStatus())
                .isEqualTo(StatusOrdemProducao.PENDENTE);
    }

    @Test
    void naoDeveCriarOrdemSemFichaTecnica() {
        Usuario usuario = criarUsuario();

        assertThatThrownBy(() -> new OrdemProducao(
                null,
                100,
                java.time.Instant.now(),
                usuario,
                usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A ficha técnica é obrigatória.");
    }

    @Test
    void deveIniciarOrdemPendente() {
        Usuario usuario = criarUsuario();

        OrdemProducao ordem = criarOrdem(usuario);

        ordem.iniciar();

        assertThat(ordem.getStatus())
                .isEqualTo(StatusOrdemProducao.EM_ANDAMENTO);

        assertThat(ordem.getIniciadaEm())
                .isNotNull();
    }

    @Test
    void naoDeveIniciarOrdemMaisDeUmaVez() {
        Usuario usuario = criarUsuario();

        OrdemProducao ordem = criarOrdem(usuario);

        ordem.iniciar();

        assertThatThrownBy(() -> ordem.iniciar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Somente ordens pendentes podem ser iniciadas.");
    }

    @Test
    void deveConcluirOrdemEmAndamento() {
        Usuario usuario = criarUsuario();

        OrdemProducao ordem = criarOrdem(usuario);

        ordem.iniciar();
        ordem.concluir(45);

        assertThat(ordem.getStatus())
                .isEqualTo(StatusOrdemProducao.CONCLUIDA);

        assertThat(ordem.getQuantidadeProduzida())
                .isEqualTo(45);

        assertThat(ordem.getConcluidaEm())
                .isNotNull();
    }

    @Test
    void naoDeveConcluirOrdemPendente() {
        Usuario usuario = criarUsuario();

        OrdemProducao ordem = criarOrdem(usuario);

        assertThatThrownBy(() -> ordem.concluir(45))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Somente ordens em andamento podem ser concluídas.");
    }

    @Test
    void naoDeveConcluirOrdemMaisDeUmaVez() {
        Usuario usuario = criarUsuario();

        OrdemProducao ordem = criarOrdem(usuario);

        ordem.iniciar();
        ordem.concluir(45);

        assertThatThrownBy(() -> ordem.concluir(50))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Somente ordens em andamento podem ser concluídas.");
    }

    @Test
    void naoDeveConcluirComQuantidadeProduzidaNula() {
        Usuario usuario = criarUsuario();

        OrdemProducao ordem = criarOrdem(usuario);

        ordem.iniciar();

        assertThatThrownBy(() -> ordem.concluir(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quantidade produzida não pode ser nula ou negativa.");
    }

    @Test
    void naoDeveConcluirComQuantidadeProduzidaNegativa() {
        Usuario usuario = criarUsuario();

        OrdemProducao ordem = criarOrdem(usuario);

        ordem.iniciar();

        assertThatThrownBy(() -> ordem.concluir(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quantidade produzida não pode ser nula ou negativa.");
    }

    @Test
    void naoDeveIniciarOrdemConcluida() {
        Usuario usuario = criarUsuario();

        OrdemProducao ordem = criarOrdem(usuario);

        ordem.iniciar();
        ordem.concluir(45);

        assertThatThrownBy(() -> ordem.iniciar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Somente ordens pendentes podem ser iniciadas.");

        assertThat(ordem.getStatus())
                .isEqualTo(StatusOrdemProducao.CONCLUIDA);
    }

    @Test
    void deveManterDadosDaOrdemAposTentativaDeAlteracao() {
        Usuario usuario = criarUsuario();

        OrdemProducao ordem = criarOrdem(usuario);

        ordem.iniciar();
        ordem.concluir(45);

        assertThatThrownBy(() -> ordem.concluir(30))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ordem.getQuantidadeProduzida()).isEqualTo(45);
        assertThat(ordem.getStatus()).isEqualTo(StatusOrdemProducao.CONCLUIDA);
    }

    private OrdemProducao criarOrdem(Usuario usuario) {
        return new OrdemProducao(
                new FichaTecnica("Pão francês", 50),
                50,
                java.time.Instant.now(),
                usuario,
                usuario);
    }

    private Usuario criarUsuario() {
        return new Usuario(
                "Funcionário Teste",
                "funcionario@teste.com",
                "hash-teste",
                Perfil.FUNCIONARIO);
    }
}
