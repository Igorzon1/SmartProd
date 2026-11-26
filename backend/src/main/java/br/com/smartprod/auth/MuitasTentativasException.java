package br.com.smartprod.auth;

/** Lançada quando o cliente ultrapassa o limite de tentativas falhas de login (HTTP 429). */
class MuitasTentativasException extends RuntimeException {

    private final long aguardarSegundos;

    MuitasTentativasException(long aguardarSegundos) {
        super("Muitas tentativas de login. Aguarde alguns minutos e tente novamente.");
        this.aguardarSegundos = Math.max(1L, aguardarSegundos);
    }

    long aguardarSegundos() {
        return aguardarSegundos;
    }
}
