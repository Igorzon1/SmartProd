package br.com.smartprod.auth;

import static java.nio.charset.StandardCharsets.UTF_8;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Configuração do JWT (RF001): assinatura e validação HS256 com o segredo resolvido de
 * {@code app.jwt.ssm-parameter} (SSM Parameter Store) ou {@code app.jwt.secret} (fallback local/dev).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /** HS256 exige chave de no mínimo 256 bits (32 bytes). */
    static final int TAMANHO_MINIMO_DO_SEGREDO = 32;

    @Bean
    SecretKey jwtSecretKey(JwtProperties propriedades) {
        return chave(propriedades);
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties propriedades, SecretKey jwtSecretKey) {
        if (propriedades.validadeMinutos() <= 0) {
            throw new IllegalStateException("app.jwt.validade-minutos (JWT_VALIDADE_MINUTOS) deve ser maior que zero.");
        }
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /** Sobrecarga usada em testes unitários diretos sem contexto Spring. */
    JwtEncoder jwtEncoder(JwtProperties propriedades) {
        return jwtEncoder(propriedades, chave(propriedades));
    }

    /** Lê o token Bearer: confere a assinatura, o prazo (exp/nbf) e o emissor ({@code smartprod}). */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.EMISSOR));
        return decoder;
    }

    /** Chave de assinatura. Falha na subida da aplicação se o segredo estiver ausente ou fraco demais. */
    static SecretKey chave(JwtProperties propriedades) {
        String segredo = resolverSegredo(propriedades);
        if (segredo == null || segredo.getBytes(UTF_8).length < TAMANHO_MINIMO_DO_SEGREDO) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET ou JWT_SECRET_SSM_PARAM) deve ter no mínimo "
                            + TAMANHO_MINIMO_DO_SEGREDO + " caracteres.");
        }
        return new SecretKeySpec(segredo.getBytes(UTF_8), "HmacSHA256");
    }

    private static String resolverSegredo(JwtProperties propriedades) {
        if (propriedades.ssmParameter() != null && !propriedades.ssmParameter().isBlank()) {
            String regiao = (propriedades.ssmRegion() == null || propriedades.ssmRegion().isBlank())
                    ? "us-east-2"
                    : propriedades.ssmRegion();
            return SsmParameterClient.buscarParametro(propriedades.ssmParameter().trim(), regiao.trim());
        }
        return propriedades.secret();
    }
}
