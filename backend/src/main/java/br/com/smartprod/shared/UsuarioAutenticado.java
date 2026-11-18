package br.com.smartprod.shared;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;

/**
 * Dados de quem está logado, para os controllers (RF001). O nome da autenticação é o id do usuário
 * (claim {@code sub} do token, ver {@code UsuarioJwtConverter}) e o perfil vira a autoridade
 * {@code ROLE_GERENTE} ou {@code ROLE_FUNCIONARIO}.
 *
 * <pre>
 * Long id = UsuarioAutenticado.id(autenticacao);
 * boolean gerente = UsuarioAutenticado.ehGerente(autenticacao);
 * </pre>
 *
 * Nos testes, use {@code @WithMockUser(username = "1", roles = "GERENTE")}: o nome precisa ser um id.
 */
public final class UsuarioAutenticado {

    private static final String ROLE_GERENTE = "ROLE_GERENTE";

    private UsuarioAutenticado() {
    }

    /**
     * Id do usuário logado.
     *
     * @throws AuthenticationCredentialsNotFoundException sem usuário logado ou sem id válido (resposta 401).
     *         Nunca devolve {@code null}: uma regra que dependa do id não pode ser pulada em silêncio.
     */
    public static Long id(Authentication autenticacao) {
        if (autenticacao == null) {
            throw semLogin();
        }
        try {
            return Long.valueOf(autenticacao.getName());
        } catch (NumberFormatException e) {
            throw semLogin();
        }
    }

    /** {@code true} se o usuário logado tem o perfil GERENTE. */
    public static boolean ehGerente(Authentication autenticacao) {
        return autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(autoridade -> ROLE_GERENTE.equals(autoridade.getAuthority()));
    }

    private static AuthenticationCredentialsNotFoundException semLogin() {
        return new AuthenticationCredentialsNotFoundException("Faça login para continuar.");
    }
}
