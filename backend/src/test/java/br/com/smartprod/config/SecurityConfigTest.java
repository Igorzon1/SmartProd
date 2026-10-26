package br.com.smartprod.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void senhaEGravadaComBcryptDe12Rounds() { // RNF-S001: mínimo de 10 rounds
        String hash = encoder.encode("senha-secreta");

        assertThat(hash).startsWith("$2a$12$");
        assertThat(encoder.matches("senha-secreta", hash)).isTrue();
        assertThat(encoder.matches("outra-senha", hash)).isFalse();
    }
}
