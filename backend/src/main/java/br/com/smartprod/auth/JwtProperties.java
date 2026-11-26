package br.com.smartprod.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propriedades do token de acesso (prefixo {@code app.jwt}).
 *
 * <p>Em produção o segredo pode vir do AWS SSM Parameter Store ({@code JWT_SECRET_SSM_PARAM})
 * ou da variável {@code JWT_SECRET} (dev/local); nunca fica no repositório.
 *
 * @param secret          segredo HS256 (mínimo 32 caracteres), quando não vier do SSM
 * @param validadeMinutos tempo de vida do token, em minutos
 * @param ssmParameter    nome do parâmetro SecureString no SSM Parameter Store (opcional)
 * @param ssmRegion       região AWS do parâmetro SSM (padrão {@code us-east-2})
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("480") long validadeMinutos,
        String ssmParameter,
        @DefaultValue("us-east-2") String ssmRegion) {

    @ConstructorBinding
    public JwtProperties {
    }

    public JwtProperties(String secret, long validadeMinutos) {
        this(secret, validadeMinutos, null, "us-east-2");
    }

    /** Impede que o segredo apareça em log se o objeto for impresso por engano. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, validadeMinutos=" + validadeMinutos
                + ", ssmParameter=" + ssmParameter + ", ssmRegion=" + ssmRegion + "]";
    }
}
