package br.com.smartprod.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    @Test
    void bloqueiaContaAposLimiteDeFalhasEVoltaALiberarAposJanela() {
        AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-11-26T12:00:00Z"));
        Clock relogio = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return agora.get();
            }
        };

        LoginRateLimiter limiter = new LoginRateLimiter(true, 5, 20, 300, relogio);
        for (int i = 0; i < 5; i++) {
            limiter.verificarBloqueio("203.0.113.10", "alvo@padaria.com");
            limiter.registrarFalha("203.0.113.10", "alvo@padaria.com");
        }

        assertThatThrownBy(() -> limiter.verificarBloqueio("203.0.113.10", "ALVO@padaria.com"))
                .isInstanceOf(MuitasTentativasException.class)
                .hasMessageContaining("Muitas tentativas");

        // Outro e-mail no mesmo IP ainda não atingiu o limite por IP (20).
        assertThatCode(() -> limiter.verificarBloqueio("203.0.113.10", "outro@padaria.com"))
                .doesNotThrowAnyException();

        // Após 301 segundos, a janela expira e volta a aceitar.
        agora.set(agora.get().plusSeconds(301));
        assertThatCode(() -> limiter.verificarBloqueio("203.0.113.10", "alvo@padaria.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void loginComSucessoZeraContagemDaConta() {
        LoginRateLimiter limiter = new LoginRateLimiter(true, 3, 20, 300, Clock.systemUTC());
        limiter.registrarFalha("203.0.113.20", "maria@padaria.com");
        limiter.registrarFalha("203.0.113.20", "maria@padaria.com");
        limiter.registrarSucesso("203.0.113.20", "maria@padaria.com");

        limiter.registrarFalha("203.0.113.20", "maria@padaria.com");
        assertThatCode(() -> limiter.verificarBloqueio("203.0.113.20", "maria@padaria.com"))
                .doesNotThrowAnyException();
    }
}
