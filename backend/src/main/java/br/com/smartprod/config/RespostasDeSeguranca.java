package br.com.smartprod.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Corpo das respostas 401 e 403 que o Spring Security dá antes de chegar ao controller (sem token, token
 * inválido ou vencido), no mesmo formato RFC 7807 e em português que o resto da API.
 */
@Component
class RespostasDeSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    RespostasDeSeguranca(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
            AuthenticationException erro) throws IOException {
        resposta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escrever(requisicao, resposta, HttpStatus.UNAUTHORIZED, "Não autenticado",
                "Faça login para continuar.");
    }

    @Override
    public void handle(HttpServletRequest requisicao, HttpServletResponse resposta,
            AccessDeniedException erro) throws IOException {
        escrever(requisicao, resposta, HttpStatus.FORBIDDEN, "Acesso negado",
                "Você não tem permissão para esta ação.");
    }

    private void escrever(HttpServletRequest requisicao, HttpServletResponse resposta, HttpStatus status,
            String titulo, String detalhe) throws IOException {
        Map<String, Object> problema = new LinkedHashMap<>();
        problema.put("type", "about:blank");
        problema.put("title", titulo);
        problema.put("status", status.value());
        problema.put("detail", detalhe);
        problema.put("instance", requisicao.getRequestURI());

        resposta.setStatus(status.value());
        resposta.setCharacterEncoding("UTF-8");
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(resposta.getOutputStream(), problema);
    }
}
