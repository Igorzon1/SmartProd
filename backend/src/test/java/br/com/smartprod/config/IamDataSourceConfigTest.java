package br.com.smartprod.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class IamDataSourceConfigTest {

    @Test
    void deveGerarTokenSigV4ValidoComSessionToken() {
        Instant instante = Instant.parse("2026-11-24T12:00:00Z");

        String token = IamDataSourceConfig.generateRdsAuthToken(
                "db.example.us-east-2.rds.amazonaws.com",
                5432,
                "us-east-2",
                "postgres",
                instante,
                "TESTACCESSKEY1234567",
                "test-secret-key-for-sigv4-unit-test-only",
                "session-token-exemplo");

        assertThat(token)
                .startsWith("db.example.us-east-2.rds.amazonaws.com:5432/?")
                .doesNotStartWith("https://")
                .contains("Action=connect")
                .contains("DBUser=postgres")
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .contains("X-Amz-Credential=TESTACCESSKEY1234567%2F20261124%2Fus-east-2%2Frds-db%2Faws4_request")
                .contains("X-Amz-Date=20261124T120000Z")
                .contains("X-Amz-Expires=900")
                .contains("X-Amz-Security-Token=session-token-exemplo")
                .contains("X-Amz-SignedHeaders=host")
                .containsPattern("X-Amz-Signature=[0-9a-f]{64}$");
    }

    @Test
    void deveGerarMesmoTokenDeFormaDeterministicaParaMesmoInstante() {
        Instant instante = Instant.parse("2026-11-24T12:00:00Z");

        String primeiro = IamDataSourceConfig.generateRdsAuthToken(
                "db.example.us-east-2.rds.amazonaws.com", 5432, "us-east-2", "postgres",
                instante, "TESTACCESSKEY0000001", "secret-example", null);
        String segundo = IamDataSourceConfig.generateRdsAuthToken(
                "db.example.us-east-2.rds.amazonaws.com", 5432, "us-east-2", "postgres",
                instante, "TESTACCESSKEY0000001", "secret-example", null);

        assertThat(primeiro).isEqualTo(segundo).doesNotContain("X-Amz-Security-Token");
    }

    @Test
    void deveFalharQuandoCredenciaisAwsAusentes() {
        Instant instante = Instant.parse("2026-11-24T12:00:00Z");

        assertThatThrownBy(() -> IamDataSourceConfig.generateRdsAuthToken(
                "db.example.us-east-2.rds.amazonaws.com", 5432, "us-east-2", "postgres",
                instante, "", "secret", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Credenciais AWS");
    }
}
