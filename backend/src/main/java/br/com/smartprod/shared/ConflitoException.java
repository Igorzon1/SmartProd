package br.com.smartprod.shared;

/**
 * Regra de negócio que impede a ação no estado atual (ex.: iniciar uma OP que não está pendente).
 * O tratador global devolve 409 com a mensagem em {@code detail}.
 *
 * <p>Estende {@link IllegalStateException} para continuar compatível com quem já tratava esse tipo.
 */
public class ConflitoException extends IllegalStateException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
