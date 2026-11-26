package br.com.smartprod.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.smartprod.auth.TokenService.TokenEmitido;
import br.com.smartprod.auth.dto.LoginRequest;
import br.com.smartprod.auth.dto.LoginResponse;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;

/** Regras de autenticação (RF001): login com rate limiting, emissão de JWT e logout com revogação. */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;
    private final LoginRateLimiter rateLimiter;

    public AuthService(AuthenticationManager authenticationManager, UsuarioRepository usuarioRepository,
            TokenService tokenService, LoginRateLimiter rateLimiter) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * @throws org.springframework.security.core.AuthenticationException se o e-mail ou a senha estiverem
     *         errados ou o usuário estiver inativo (o controller devolve 401 sem dizer qual dos casos).
     * @throws MuitasTentativasException se o cliente ultrapassar o limite de falhas recentes (429).
     */
    public LoginResponse login(LoginRequest requisicao, String ipCliente) {
        rateLimiter.verificarBloqueio(ipCliente, requisicao.email());

        Authentication autenticacao;
        try {
            autenticacao = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(requisicao.email(), requisicao.senha()));
        } catch (AuthenticationException erro) {
            rateLimiter.registrarFalha(ipCliente, requisicao.email());
            throw erro;
        }

        rateLimiter.registrarSucesso(ipCliente, requisicao.email());

        Usuario usuario = usuarioRepository.findByEmail(autenticacao.getName())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado"));
        TokenEmitido token = tokenService.emitir(usuario);

        return new LoginResponse(token.valor(), LoginResponse.TIPO_BEARER, token.expiraEm(),
                new LoginResponse.UsuarioLogado(usuario.getId(), usuario.getNome(), usuario.getPerfil()));
    }

    /** Incrementa a versão de token do usuário, invalidando imediatamente todos os JWTs já emitidos. */
    @Transactional
    public void logout(Long usuarioId) {
        usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
            usuario.revogarTokens();
            usuarioRepository.save(usuario);
        });
    }
}
