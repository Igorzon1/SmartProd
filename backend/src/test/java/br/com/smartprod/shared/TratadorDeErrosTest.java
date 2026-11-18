package br.com.smartprod.shared;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Todo erro da API sai em RFC 7807 e em português, inclusive o 401 e o 403 da segurança. O teste usa um
 * controller só dele para provocar cada tipo de erro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TratadorDeErrosTest.ControllerDeTeste.class)
class TratadorDeErrosTest {

    private static final String BASE = "/api/teste-erros";

    @Autowired
    private MockMvc mockMvc;

    // ---- regras de negócio

    @Test
    @WithMockUser(username = "1")
    void regraDeNegocioViraStatusComAMensagemEmPortugues() throws Exception {
        problema(get(BASE + "/dados-invalidos"), 400, "Requisição inválida", "A quantidade deve ser maior que zero.");
        problema(get(BASE + "/conflito"), 409, "Conflito", "Somente ordens pendentes podem ser iniciadas.");
        problema(get(BASE + "/nao-encontrado"), 404, "Não encontrado", "Ordem de produção não encontrada.");
        problema(get(BASE + "/response-status"), 404, "Não encontrado", "Ficha técnica não encontrada.");
        problema(get(BASE + "/concorrencia"), 409, "Conflito",
                "Outra pessoa alterou este registro ao mesmo tempo. Atualize a tela e tente de novo.");
        problema(get(BASE + "/integridade"), 409, "Conflito", "Os dados conflitam com um registro que já existe.");
    }

    /** Uma IllegalStateException qualquer (do Spring, do Hibernate...) não pode virar 409 nem vazar a mensagem. */
    @Test
    @WithMockUser(username = "1")
    void erroInesperadoDa500SemMostrarDetalheInterno() throws Exception {
        problema(get(BASE + "/inesperado"), 500, "Erro interno",
                "Não foi possível concluir a solicitação. Tente de novo; se continuar, avise o suporte.")
                .andExpect(content().string(not(containsString("detalhe interno"))));
    }

    // ---- erros do Spring MVC

    @Test
    @WithMockUser(username = "1")
    void camposInvalidosVemListadosEmPortugues() throws Exception {
        mockMvc.perform(post(BASE + "/validacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\" \",\"quantidade\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.campos.nome").value("não deve estar em branco"))
                .andExpect(jsonPath("$.campos.quantidade").value("deve ser maior que 0"))
                .andExpect(jsonPath("$.detail").value(containsString("nome: não deve estar em branco")));
    }

    @Test
    @WithMockUser(username = "1")
    void corpoParametroEEnderecoInvalidosTemMensagemEmPortugues() throws Exception {
        problema(post(BASE + "/validacao").contentType(MediaType.APPLICATION_JSON).content("{nao e json"),
                400, "Requisição inválida", "O corpo da requisição está vazio ou fora do formato esperado.");
        problema(get(BASE + "/numero?valor=abc"), 400, "Requisição inválida",
                "O valor 'abc' não é válido para 'valor'.");
        problema(get(BASE + "/numero"), 400, "Requisição inválida", "Informe o parâmetro 'valor'.");
        problema(get("/api/endereco-que-nao-existe"), 404, "Não encontrado", "Endereço não encontrado.");
    }

    // ---- segurança

    @Test
    void semTokenOuComTokenInvalidoDa401ComCorpo() throws Exception {
        problema(get(BASE + "/conflito"), 401, "Não autenticado", "Faça login para continuar.")
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        problema(get(BASE + "/conflito").header("Authorization", "Bearer nao-e-um-token"), 401,
                "Não autenticado", "Faça login para continuar.");
    }

    @Test
    @WithMockUser(username = "2", roles = "FUNCIONARIO")
    void perfilSemPermissaoDa403ComCorpo() throws Exception {
        problema(get(BASE + "/so-gerente"), 403, "Acesso negado", "Você não tem permissão para esta ação.");
    }

    @Test
    @WithMockUser(username = "user")
    void usuarioSemIdValidoDa401() throws Exception {
        problema(get(BASE + "/usuario-logado"), 401, "Não autenticado", "Faça login para continuar.");
    }

    /** O tratador do login tem prioridade: a mensagem continua a mesma para e-mail e senha errados. */
    @Test
    void loginComSenhaErradaContinuaComAMensagemDoLogin() throws Exception {
        problema(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ninguem@padaria.com\",\"senha\":\"senha-errada-123\"}"),
                401, "Credenciais inválidas", "E-mail ou senha inválidos.");
    }

    @Test
    void swaggerTemOBotaoDeTokenEOLoginFicaSemCadeado() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.token.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].token").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty());
    }

    private ResultActions problema(org.springframework.test.web.servlet.RequestBuilder requisicao, int status,
            String titulo, String detalhe) throws Exception {
        return mockMvc.perform(requisicao)
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").value(titulo))
                .andExpect(jsonPath("$.detail").value(detalhe));
    }

    record Corpo(@NotBlank String nome, @NotNull @Positive Integer quantidade) {
    }

    @RestController
    @RequestMapping(BASE)
    static class ControllerDeTeste {

        @GetMapping("/dados-invalidos")
        void dadosInvalidos() {
            throw new DadosInvalidosException("A quantidade deve ser maior que zero.");
        }

        @GetMapping("/conflito")
        void conflito() {
            throw new ConflitoException("Somente ordens pendentes podem ser iniciadas.");
        }

        @GetMapping("/nao-encontrado")
        void naoEncontrado() {
            throw new EntityNotFoundException("Ordem de produção não encontrada.");
        }

        @GetMapping("/response-status")
        void responseStatus() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ficha técnica não encontrada.");
        }

        @GetMapping("/concorrencia")
        void concorrencia() {
            throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
        }

        @GetMapping("/integridade")
        void integridade() {
            throw new DataIntegrityViolationException("uk_teste");
        }

        @GetMapping("/inesperado")
        void inesperado() {
            throw new IllegalStateException("detalhe interno do framework");
        }

        @GetMapping("/numero")
        int numero(@RequestParam int valor) {
            return valor;
        }

        @PostMapping("/validacao")
        Corpo validacao(@Valid @RequestBody Corpo corpo) {
            return corpo;
        }

        @GetMapping("/so-gerente")
        @PreAuthorize("hasRole('GERENTE')")
        String soGerente() {
            return "ok";
        }

        @GetMapping("/usuario-logado")
        Long usuarioLogado(Authentication autenticacao) {
            return UsuarioAutenticado.id(autenticacao);
        }
    }
}
