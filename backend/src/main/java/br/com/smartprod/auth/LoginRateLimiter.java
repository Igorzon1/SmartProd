package br.com.smartprod.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limitador de tentativas falhas de login (janela deslizante em memória por e-mail+IP e por IP).
 *
 * <p>Bloqueia com {@link MuitasTentativasException} (HTTP 429) após excesso de falhas consecutivas
 * dentro da janela configurada e zera a contagem da conta ao autenticar com sucesso.
 */
@Component
class LoginRateLimiter {

    private static final int LIMITE_CHAVES_EM_MEMORIA = 2_000;

    private final boolean habilitado;
    private final int maxTentativasConta;
    private final int maxTentativasIp;
    private final long janelaSegundos;
    private final Clock relogio;

    private final ConcurrentHashMap<String, Deque<Instant>> falhasPorConta = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> falhasPorIp = new ConcurrentHashMap<>();

    @Autowired
    LoginRateLimiter(
            @Value("${app.auth.rate-limit.habilitado:true}") boolean habilitado,
            @Value("${app.auth.rate-limit.max-tentativas-conta:5}") int maxTentativasConta,
            @Value("${app.auth.rate-limit.max-tentativas-ip:20}") int maxTentativasIp,
            @Value("${app.auth.rate-limit.janela-segundos:300}") long janelaSegundos) {
        this(habilitado, maxTentativasConta, maxTentativasIp, janelaSegundos, Clock.systemUTC());
    }

    LoginRateLimiter(boolean habilitado, int maxTentativasConta, int maxTentativasIp, long janelaSegundos,
            Clock relogio) {
        this.habilitado = habilitado;
        this.maxTentativasConta = maxTentativasConta;
        this.maxTentativasIp = maxTentativasIp;
        this.janelaSegundos = janelaSegundos;
        this.relogio = relogio;
    }

    void verificarBloqueio(String ip, String email) {
        if (!habilitado) {
            return;
        }
        Instant agora = relogio.instant();
        String chaveIp = normalizarIp(ip);
        String chaveConta = chaveIp + "|" + normalizarEmail(email);

        long esperaConta = tempoDeEspera(falhasPorConta, chaveConta, maxTentativasConta, agora);
        long esperaIp = tempoDeEspera(falhasPorIp, chaveIp, maxTentativasIp, agora);
        long espera = Math.max(esperaConta, esperaIp);
        if (espera > 0) {
            throw new MuitasTentativasException(espera);
        }
    }

    void registrarFalha(String ip, String email) {
        if (!habilitado) {
            return;
        }
        Instant agora = relogio.instant();
        String chaveIp = normalizarIp(ip);
        String chaveConta = chaveIp + "|" + normalizarEmail(email);

        adicionarEvento(falhasPorConta, chaveConta, agora);
        adicionarEvento(falhasPorIp, chaveIp, agora);
    }

    void registrarSucesso(String ip, String email) {
        if (!habilitado) {
            return;
        }
        String chaveConta = normalizarIp(ip) + "|" + normalizarEmail(email);
        falhasPorConta.remove(chaveConta);
    }

    private long tempoDeEspera(ConcurrentHashMap<String, Deque<Instant>> mapa, String chave, int limite,
            Instant agora) {
        Deque<Instant> eventos = mapa.get(chave);
        if (eventos == null) {
            return 0;
        }
        Instant corte = agora.minusSeconds(janelaSegundos);
        synchronized (eventos) {
            limparAntigos(eventos, corte);
            if (eventos.size() < limite) {
                if (eventos.isEmpty()) {
                    mapa.remove(chave, eventos);
                }
                return 0;
            }
            Instant maisAntigo = eventos.peekFirst();
            if (maisAntigo == null) {
                return janelaSegundos;
            }
            long restante = maisAntigo.plusSeconds(janelaSegundos).getEpochSecond() - agora.getEpochSecond();
            return Math.max(1L, restante);
        }
    }

    private void adicionarEvento(ConcurrentHashMap<String, Deque<Instant>> mapa, String chave, Instant agora) {
        if (mapa.size() > LIMITE_CHAVES_EM_MEMORIA) {
            expurgarMapa(mapa, agora.minusSeconds(janelaSegundos));
        }
        Deque<Instant> eventos = mapa.computeIfAbsent(chave, k -> new ArrayDeque<>());
        Instant corte = agora.minusSeconds(janelaSegundos);
        synchronized (eventos) {
            limparAntigos(eventos, corte);
            eventos.addLast(agora);
        }
    }

    private static void limparAntigos(Deque<Instant> eventos, Instant corte) {
        while (!eventos.isEmpty() && !eventos.peekFirst().isAfter(corte)) {
            eventos.pollFirst();
        }
    }

    private static void expurgarMapa(ConcurrentHashMap<String, Deque<Instant>> mapa, Instant corte) {
        mapa.forEach((chave, eventos) -> {
            synchronized (eventos) {
                limparAntigos(eventos, corte);
                if (eventos.isEmpty()) {
                    mapa.remove(chave, eventos);
                }
            }
        });
    }

    private static String normalizarIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "desconhecido";
        }
        return ip.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizarEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
