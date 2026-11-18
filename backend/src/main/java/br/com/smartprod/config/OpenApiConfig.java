package br.com.smartprod.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Swagger UI com o botão "Authorize": faça login em {@code POST /api/auth/login}, copie o {@code token}
 * da resposta e cole no botão. As demais chamadas passam a levar {@code Authorization: Bearer <token>}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "SmartProd API", version = "v1",
                description = "Gestão de produção para padarias e confeitarias"),
        security = @SecurityRequirement(name = OpenApiConfig.ESQUEMA_TOKEN))
@SecurityScheme(name = OpenApiConfig.ESQUEMA_TOKEN, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT", description = "Token devolvido por POST /api/auth/login")
public class OpenApiConfig {

    static final String ESQUEMA_TOKEN = "token";
}
