package br.com.smartprod.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;

class StaticFrontendConfigTest {

    private final OncePerRequestFilter filtro = new StaticFrontendConfig().utf8ContentTypeFilter().getFilter();

    @Test
    void deveAdicionarCharsetUtf8EmAssetsTextuais() throws Exception {
        assertContentType("text/html", "text/html;charset=UTF-8");
        assertContentType("text/css", "text/css;charset=UTF-8");
        assertContentType("application/javascript", "application/javascript;charset=UTF-8");
        assertContentType("image/svg+xml", "image/svg+xml;charset=UTF-8");
    }

    @Test
    void naoDeveAlterarCharsetExistenteJsonOuConteudoBinario() throws Exception {
        assertContentType("text/html; charset=UTF-8", "text/html; charset=UTF-8");
        assertContentType("application/problem+json", "application/problem+json");
        assertContentType("application/json", "application/json");
        assertContentType("image/png", "image/png");
    }

    private void assertContentType(String entrada, String esperado) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/teste");
        MockHttpServletResponse res = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> response.setContentType(entrada);
        filtro.doFilter(req, res, chain);

        assertThat(res.getContentType()).isEqualTo(esperado);
    }
}
