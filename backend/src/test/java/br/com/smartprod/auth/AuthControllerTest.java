package br.com.smartprod.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.SeedGerenteProperties;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;

/** Login de ponta a ponta: controller, Spring Security, BCrypt, H2 e emissão do token. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // cada teste desfaz os usuários que criou
class AuthControllerTest {

    private static final String SENHA = "senha-de-teste-123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private SeedGerenteProperties seedGerente;

    @Test
    void loginValidoDevolveTokenEDadosDoUsuario() throws Exception {
        Usuario gerente = criarUsuario("maria.teste@padaria.com", Perfil.GERENTE, true);

        MvcResult resultado = login("maria.teste@padaria.com", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").value(gerente.getId()))
                .andExpect(jsonPath("$.usuario.nome").value("Maria Teste"))
                .andExpect(jsonPath("$.usuario.perfil").value("GERENTE"))
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        Jwt jwt = decodificar(JsonPath.read(corpo, "$.token"));
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(gerente.getId()));
        assertThat(jwt.getClaimAsString("perfil")).isEqualTo("GERENTE");
        assertThat(Instant.parse(JsonPath.read(corpo, "$.expiraEm"))).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void senhaAcimaDoLimiteDoBcryptNoLoginRetorna401() throws Exception {
        criarUsuario("maria.teste@padaria.com", Perfil.GERENTE, true);

        login("maria.teste@padaria.com", "a".repeat(128)).andExpect(status().isUnauthorized());
        login("ninguem.teste@padaria.com", "a".repeat(128)).andExpect(status().isUnauthorized());
    }

    @Test
    void funcionarioRecebeOPerfilFuncionario() throws Exception {
        criarUsuario("beto.teste@padaria.com", Perfil.FUNCIONARIO, true);

        login("beto.teste@padaria.com", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("FUNCIONARIO"));
    }

    @Test
    void respostaNuncaTrazSenhaNemHash() throws Exception {
        criarUsuario("maria.teste@padaria.com", Perfil.GERENTE, true);

        login("maria.teste@padaria.com", SENHA)
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("$2a$"))))
                .andExpect(content().string(not(containsString("senha"))));
    }

    @Test
    void emailComEspacosEMaiusculasEAceito() throws Exception {
        criarUsuario("maria.teste@padaria.com", Perfil.GERENTE, true);

        login("  MARIA.Teste@Padaria.com ", SENHA).andExpect(status().isOk());
    }

    @Test
    void senhaErradaRetorna401SemRevelarOMotivo() throws Exception {
        criarUsuario("maria.teste@padaria.com", Perfil.GERENTE, true);

        login("maria.teste@padaria.com", "senha-errada")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Credenciais inválidas"))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void emailInexistenteRetorna401ComAMesmaMensagem() throws Exception {
        login("ninguem@padaria.com", SENHA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void usuarioInativoRetorna401MesmoComSenhaCorreta() throws Exception { // RF002: inativo não entra
        criarUsuario("inativo.teste@padaria.com", Perfil.FUNCIONARIO, false);

        login("inativo.teste@padaria.com", SENHA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void corpoSemSenhaOuComCamposVaziosRetorna400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"maria@padaria.com\"}"))
                .andExpect(status().isBadRequest());

        login("", "").andExpect(status().isBadRequest());
    }

    @Test
    void gerenteDaSeedDeDesenvolvimentoConsegueEntrar() throws Exception {
        login(seedGerente.email(), seedGerente.senha())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("GERENTE"));
    }

    @Test
    void logoutRevogaOTokenImediatamenteENovoLoginVoltaAFuncionar() throws Exception {
        criarUsuario("logout.teste@padaria.com", Perfil.GERENTE, true);

        MvcResult entrada = login("logout.teste@padaria.com", SENHA)
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(entrada.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/insumos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/insumos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        MvcResult novaEntrada = login("logout.teste@padaria.com", SENHA)
                .andExpect(status().isOk())
                .andReturn();
        String novoToken = JsonPath.read(novaEntrada.getResponse().getContentAsString(), "$.token");
        mockMvc.perform(get("/api/insumos").header("Authorization", "Bearer " + novoToken))
                .andExpect(status().isOk());
    }

    @Test
    void excessoDeFalhasNoLoginRetorna429ComRetryAfter() throws Exception {
        String email = "forca.bruta@padaria.com";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", "198.51.100.77")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"senha\":\"senha-errada-123\"}".formatted(email)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "198.51.100.77")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"senha-errada-123\"}".formatted(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Muitas tentativas"));
    }

    private Usuario criarUsuario(String email, Perfil perfil, boolean ativo) {
        Usuario usuario = new Usuario("Maria Teste", email, passwordEncoder.encode(SENHA), perfil);
        if (!ativo) {
            usuario.inativar();
        }
        return usuarioRepository.saveAndFlush(usuario);
    }

    private ResultActions login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)));
    }

    private Jwt decodificar(String token) {
        return NimbusJwtDecoder.withSecretKey(JwtConfig.chave(jwtProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build()
                .decode(token); // valida assinatura e prazo de verdade
    }
}
