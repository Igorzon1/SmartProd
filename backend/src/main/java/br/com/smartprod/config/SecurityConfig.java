package br.com.smartprod.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.com.smartprod.auth.UsuarioJwtConverter;
import jakarta.servlet.http.HttpServletRequest;

/**
 * API stateless: sem sessão e sem CSRF, pois a autenticação é por token JWT (RF001).
 *
 * <p>O login ({@code POST /api/auth/login}) é público; a documentação Swagger/OpenAPI é liberada
 * apenas fora do perfil {@code prod}. Todo o resto exige um token válido no cabeçalho
 * {@code Authorization: Bearer <token>}: sem token, ou com token inválido, vencido, revogado no
 * logout, de outro emissor ou de usuário inexistente/inativo, a resposta é 401 (RFC 7807).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** RNF-S001: BCrypt com no mínimo 10 rounds; o projeto usa 12. */
    static final int BCRYPT_STRENGTH = 12;

    static final String POLITICA_CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; "
                    + "object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    private static final String CAMINHO_DO_LOGIN = "/api/auth/login";
    private static final BearerTokenResolver RESOLVEDOR_PADRAO = new DefaultBearerTokenResolver();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioJwtConverter conversorDeJwt,
            RespostasDeSeguranca respostas, Environment environment) throws Exception {
        boolean producao = environment.acceptsProfiles(Profiles.of("prod"));

        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .referrerPolicy(ref -> ref.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(POLITICA_CSP))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000L)
                                .requestMatcher(AnyRequestMatcher.INSTANCE)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(respostas).accessDeniedHandler(respostas))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(respostas)
                        .accessDeniedHandler(respostas)
                        .bearerTokenResolver(SecurityConfig::resolverToken)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeJwt)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/css/**", "/js/**", "/pages/**").permitAll()
                            .requestMatchers(HttpMethod.HEAD, "/", "/index.html", "/favicon.ico", "/css/**", "/js/**", "/pages/**").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                            .requestMatchers(HttpMethod.POST, CAMINHO_DO_LOGIN).permitAll();
                    if (!producao) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }

    /**
     * No login o cabeçalho Authorization é ignorado: um token antigo ou vencido ainda guardado no
     * navegador não pode impedir a pessoa de entrar de novo.
     */
    static String resolverToken(HttpServletRequest requisicao) {
        if (CAMINHO_DO_LOGIN.equals(requisicao.getRequestURI())) {
            return null;
        }
        return RESOLVEDOR_PADRAO.resolve(requisicao);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
