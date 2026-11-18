package br.com.smartprod.shared;

/**
 * Dado recusado por uma regra de negócio (ex.: quantidade menor que zero). O tratador global devolve
 * 400 com a mensagem em {@code detail}.
 *
 * <p>Estende {@link IllegalArgumentException} para continuar compatível com quem já tratava esse tipo.
 */
public class DadosInvalidosException extends IllegalArgumentException {

    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }
}
