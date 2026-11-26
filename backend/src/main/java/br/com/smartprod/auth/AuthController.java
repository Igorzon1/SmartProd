package br.com.smartprod.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.smartprod.auth.dto.LoginRequest;
import br.com.smartprod.auth.dto.LoginResponse;
import br.com.smartprod.shared.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login, logout e token de acesso (RF001)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica por e-mail e senha e devolve o token JWT")
    @SecurityRequirements // o login não exige token
    @ApiResponse(responseCode = "200", description = "Login realizado")
    @ApiResponse(responseCode = "400", description = "E-mail ou senha ausentes", content = @Content)
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos, ou usuário inativo",
            content = @Content)
    @ApiResponse(responseCode = "429", description = "Muitas tentativas falhas de login", content = @Content)
    public LoginResponse login(@Valid @RequestBody LoginRequest requisicao, HttpServletRequest httpRequest) {
        return authService.login(requisicao, extrairIpCliente(httpRequest));
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerra a sessão atual e revoga imediatamente o token JWT no servidor")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada")
    @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content)
    public ResponseEntity<Void> logout(Authentication autenticacao) {
        authService.logout(UsuarioAutenticado.id(autenticacao));
        return ResponseEntity.noContent().build();
    }

    static String extrairIpCliente(HttpServletRequest requisicao) {
        String forwarded = requisicao.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int virgula = forwarded.indexOf(',');
            String primeiro = virgula >= 0 ? forwarded.substring(0, virgula) : forwarded;
            if (!primeiro.isBlank()) {
                return primeiro.trim();
            }
        }
        return requisicao.getRemoteAddr();
    }
}
