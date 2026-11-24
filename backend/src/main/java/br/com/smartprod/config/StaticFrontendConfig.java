package br.com.smartprod.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Configura o redirecionamento da raiz para {@code index.html} e garante {@code charset=UTF-8}
 * nos assets estáticos textuais (HTML, JS, CSS e SVG) sem alterar {@code application/problem+json}
 * ou conteúdos binários.
 */
@Configuration
public class StaticFrontendConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> utf8ContentTypeFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new Utf8ContentTypeFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    private static final class Utf8ContentTypeFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());
            filterChain.doFilter(request, new Utf8ResponseWrapper(response));
        }
    }

    private static final class Utf8ResponseWrapper extends HttpServletResponseWrapper {

        Utf8ResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setContentType(String type) {
            super.setContentType(ensureUtf8(type));
        }

        @Override
        public void setHeader(String name, String value) {
            if ("content-type".equalsIgnoreCase(name)) {
                super.setHeader(name, ensureUtf8(value));
                return;
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if ("content-type".equalsIgnoreCase(name)) {
                super.addHeader(name, ensureUtf8(value));
                return;
            }
            super.addHeader(name, value);
        }

        private static String ensureUtf8(String contentType) {
            if (contentType == null || contentType.isBlank()) {
                return contentType;
            }
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.contains("charset=")) {
                return contentType;
            }
            if (lower.startsWith("text/")
                    || lower.startsWith("application/javascript")
                    || lower.startsWith("image/svg+xml")) {
                return contentType + ";charset=UTF-8";
            }
            return contentType;
        }
    }
}
