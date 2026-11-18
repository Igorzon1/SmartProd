package br.com.smartprod.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.EntityNotFoundException;

/**
 * Todo erro da API sai em RFC 7807 ({@code application/problem+json}) com título e mensagem em
 * português. O frontend mostra o {@code detail} na tela.
 *
 * <ul>
 * <li>400: campos inválidos (a lista vai em {@code campos}), corpo ilegível, parâmetro inválido ou
 * {@link DadosInvalidosException};</li>
 * <li>401: sem login; 403: perfil sem permissão;</li>
 * <li>404: {@link EntityNotFoundException} ou endereço inexistente;</li>
 * <li>409: {@link ConflitoException}, registro duplicado ou alteração simultânea;</li>
 * <li>500: qualquer outro erro, com detalhe genérico (o erro real vai para o log).</li>
 * </ul>
 *
 * Os services também podem lançar {@code ResponseStatusException} com a mensagem em português.
 * Tratadores de um pacote só (como o do login) têm prioridade sobre este.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class TratadorDeErros extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    private static final Map<Integer, String> TITULOS = Map.of(
            400, "Requisição inválida",
            401, "Não autenticado",
            403, "Acesso negado",
            404, "Não encontrado",
            405, "Método não permitido",
            409, "Conflito",
            415, "Formato não suportado",
            500, "Erro interno");

    // ---- erros de regra de negócio

    @ExceptionHandler(DadosInvalidosException.class)
    public ProblemDetail dadosInvalidos(DadosInvalidosException erro) {
        return problema(HttpStatus.BAD_REQUEST, erro.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail naoEncontrado(EntityNotFoundException erro) {
        return problema(HttpStatus.NOT_FOUND, erro.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException erro) {
        return problema(HttpStatus.CONFLICT, erro.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail alteracaoSimultanea(ObjectOptimisticLockingFailureException erro) {
        return problema(HttpStatus.CONFLICT,
                "Outra pessoa alterou este registro ao mesmo tempo. Atualize a tela e tente de novo.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridade(DataIntegrityViolationException erro) {
        log.warn("Violação de integridade no banco: {}", erro.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "Os dados conflitam com um registro que já existe.");
    }

    // ---- segurança dentro dos controllers (o 401/403 antes do controller fica em config/RespostasDeSeguranca)

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail acessoNegado(AccessDeniedException erro) {
        return problema(HttpStatus.FORBIDDEN, "Você não tem permissão para esta ação.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail naoAutenticado(AuthenticationException erro) {
        return problema(HttpStatus.UNAUTHORIZED, "Faça login para continuar.");
    }

    // ---- qualquer outro erro

    @ExceptionHandler(Exception.class)
    public ProblemDetail erroInesperado(Exception erro) {
        log.error("Erro não tratado na API", erro);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR,
                "Não foi possível concluir a solicitação. Tente de novo; se continuar, avise o suporte.");
    }

    // ---- erros do Spring MVC, com mensagem em português

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException erro,
            HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        Map<String, String> campos = new LinkedHashMap<>();
        erro.getBindingResult().getFieldErrors()
                .forEach(campo -> campos.putIfAbsent(campo.getField(), campo.getDefaultMessage()));
        erro.getBindingResult().getGlobalErrors()
                .forEach(global -> campos.putIfAbsent(global.getObjectName(), global.getDefaultMessage()));

        String lista = campos.entrySet().stream()
                .map(campo -> campo.getKey() + ": " + campo.getValue())
                .collect(Collectors.joining("; "));
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Confira os campos: " + lista + ".");
        problema.setTitle("Dados inválidos");
        problema.setProperty("campos", campos);
        return handleExceptionInternal(erro, problema, headers, status, requisicao);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException erro,
            HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        return handleExceptionInternal(erro,
                problema(HttpStatus.BAD_REQUEST, "O corpo da requisição está vazio ou fora do formato esperado."),
                headers, status, requisicao);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException erro, HttpHeaders headers,
            HttpStatusCode status, WebRequest requisicao) {
        String parametro = erro instanceof MethodArgumentTypeMismatchException argumento
                ? argumento.getName()
                : erro.getPropertyName();
        return handleExceptionInternal(erro,
                problema(HttpStatus.BAD_REQUEST,
                        "O valor '" + erro.getValue() + "' não é válido para '" + parametro + "'."),
                headers, status, requisicao);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException erro, HttpHeaders headers, HttpStatusCode status,
            WebRequest requisicao) {
        return handleExceptionInternal(erro,
                problema(HttpStatus.BAD_REQUEST, "Informe o parâmetro '" + erro.getParameterName() + "'."),
                headers, status, requisicao);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException erro,
            HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        return handleExceptionInternal(erro, problema(HttpStatus.NOT_FOUND, "Endereço não encontrado."),
                headers, status, requisicao);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException erro, HttpHeaders headers, HttpStatusCode status,
            WebRequest requisicao) {
        return handleExceptionInternal(erro,
                problema(HttpStatus.METHOD_NOT_ALLOWED, "Este endereço não aceita " + erro.getMethod() + "."),
                headers, status, requisicao);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException erro,
            HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        return handleExceptionInternal(erro,
                problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envie o corpo em JSON (Content-Type: application/json)."),
                headers, status, requisicao);
    }

    /** Troca o título padrão em inglês ("Not Found", "Conflict"...) pelo título em português. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception erro, Object corpo, HttpHeaders headers,
            HttpStatusCode status, WebRequest requisicao) {
        ResponseEntity<Object> resposta = super.handleExceptionInternal(erro, corpo, headers, status, requisicao);
        if (resposta != null && resposta.getBody() instanceof ProblemDetail problema) {
            traduzirTitulo(problema);
        }
        return resposta;
    }

    private static ProblemDetail problema(HttpStatus status, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(TITULOS.getOrDefault(status.value(), status.getReasonPhrase()));
        return problema;
    }

    private static void traduzirTitulo(ProblemDetail problema) {
        HttpStatus status = HttpStatus.resolve(problema.getStatus());
        if (status != null && (problema.getTitle() == null || problema.getTitle().equals(status.getReasonPhrase()))) {
            problema.setTitle(TITULOS.getOrDefault(status.value(), status.getReasonPhrase()));
        }
    }
}
