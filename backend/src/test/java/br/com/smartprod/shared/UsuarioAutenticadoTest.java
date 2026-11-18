package br.com.smartprod.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;

class UsuarioAutenticadoTest {

    @Test
    void devolveOIdQueVemNoNomeDaAutenticacao() {
        assertThat(UsuarioAutenticado.id(new TestingAuthenticationToken("42", null, "ROLE_FUNCIONARIO")))
                .isEqualTo(42L);
    }

    /** Antes, um nome que não era id virava null e a regra "não pode inativar a si mesmo" era pulada. */
    @Test
    void semIdValidoRecusaEmVezDeDevolverNulo() {
        assertThatThrownBy(() -> UsuarioAutenticado.id(new TestingAuthenticationToken("user", null)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> UsuarioAutenticado.id(null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void reconheceOGerentePeloPerfil() {
        assertThat(UsuarioAutenticado.ehGerente(new TestingAuthenticationToken("1", null, "ROLE_GERENTE"))).isTrue();
        assertThat(UsuarioAutenticado.ehGerente(new TestingAuthenticationToken("2", null, "ROLE_FUNCIONARIO")))
                .isFalse();
        assertThat(UsuarioAutenticado.ehGerente(null)).isFalse();
    }
}
