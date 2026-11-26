package br.com.smartprod.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpRequest;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class SsmParameterClientTest {

    @Test
    void montaRequisicaoSigV4DeterministicaComCabecalhosEsperados() {
        Instant agora = Instant.parse("2026-11-26T12:00:00Z");
        String payload = "{\"Name\":\"/smartprod/prod/jwt-secret\",\"WithDecryption\":true}";

        HttpRequest req1 = SsmParameterClient.montarRequisicao(
                "us-east-2", payload, agora, "AKIAEXAMPLE", "secret-example", "session-tok");
        HttpRequest req2 = SsmParameterClient.montarRequisicao(
                "us-east-2", payload, agora, "AKIAEXAMPLE", "secret-example", "session-tok");

        assertThat(req1.uri().toString()).isEqualTo("https://ssm.us-east-2.amazonaws.com/");
        assertThat(req1.headers().firstValue("X-Amz-Target")).contains("AmazonSSM.GetParameter");
        assertThat(req1.headers().firstValue("X-Amz-Date")).contains("20261126T120000Z");
        assertThat(req1.headers().firstValue("X-Amz-Security-Token")).contains("session-tok");
        assertThat(req1.headers().firstValue("Authorization").orElse(""))
                .startsWith("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLE/20261126/us-east-2/ssm/aws4_request")
                .isEqualTo(req2.headers().firstValue("Authorization").orElse(""));
    }

    @Test
    void extraiValorDoParametroDoJsonSsm() {
        String json = "{\"Parameter\":{\"Name\":\"/smartprod/prod/jwt-secret\",\"Type\":\"SecureString\",\"Value\":\"segredo-32-caracteres-de-teste-ok\"}}";

        assertThat(SsmParameterClient.extrairValor(json, "/smartprod/prod/jwt-secret"))
                .isEqualTo("segredo-32-caracteres-de-teste-ok");
    }

    @Test
    void falhaQuandoFaltamCredenciaisAws() {
        assertThatThrownBy(() -> SsmParameterClient.buscarParametro(
                "/smartprod/prod/jwt-secret", "us-east-2", Instant.now(), null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Credenciais AWS");
    }
}
