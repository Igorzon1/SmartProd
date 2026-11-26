package br.com.smartprod.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lê parâmetros {@code SecureString} do AWS SSM Parameter Store via assinatura SigV4 pura em Java 21,
 * usando as credenciais nativas do ambiente Lambda (sem adicionar dependências extras do AWS SDK ao ZIP).
 */
final class SsmParameterClient {

    private static final DateTimeFormatter AMZ_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SsmParameterClient() {
    }

    static String buscarParametro(String nomeParametro, String regiao) {
        return buscarParametro(
                nomeParametro,
                regiao,
                Instant.now(),
                System.getenv("AWS_ACCESS_KEY_ID"),
                System.getenv("AWS_SECRET_ACCESS_KEY"),
                System.getenv("AWS_SESSION_TOKEN"));
    }

    static String buscarParametro(String nomeParametro, String regiao, Instant agora,
            String accessKey, String secretKey, String sessionToken) {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "Credenciais AWS (AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY) ausentes para ler parâmetro SSM.");
        }
        try {
            String payload = MAPPER.writeValueAsString(java.util.Map.of(
                    "Name", nomeParametro,
                    "WithDecryption", true));
            HttpRequest requisicao = montarRequisicao(regiao, payload, agora, accessKey, secretKey, sessionToken);
            HttpClient cliente = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() != 200) {
                throw new IllegalStateException(
                        "Falha ao ler parâmetro SSM '" + nomeParametro + "' (HTTP " + resposta.statusCode() + ").");
            }
            return extrairValor(resposta.body(), nomeParametro);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao consultar parâmetro SSM '" + nomeParametro + "'.", e);
        }
    }

    static HttpRequest montarRequisicao(String regiao, String payload, Instant agora,
            String accessKey, String secretKey, String sessionToken) {
        String host = "ssm." + regiao + ".amazonaws.com";
        String target = "AmazonSSM.GetParameter";
        String contentType = "application/x-amz-json-1.1";
        String amzDate = AMZ_DATE_FMT.format(agora);
        String dateStamp = DATE_STAMP_FMT.format(agora);
        String payloadHash = sha256Hex(payload);
        String scope = dateStamp + "/" + regiao + "/ssm/aws4_request";

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaders = new StringBuilder();
        canonicalHeaders.append("content-type:").append(contentType).append('\n');
        signedHeaders.append("content-type;");
        canonicalHeaders.append("host:").append(host).append('\n');
        signedHeaders.append("host;");
        canonicalHeaders.append("x-amz-date:").append(amzDate).append('\n');
        signedHeaders.append("x-amz-date;");
        if (sessionToken != null && !sessionToken.isBlank()) {
            canonicalHeaders.append("x-amz-security-token:").append(sessionToken).append('\n');
            signedHeaders.append("x-amz-security-token;");
        }
        canonicalHeaders.append("x-amz-target:").append(target).append('\n');
        signedHeaders.append("x-amz-target");

        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest);

        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, regiao);
        byte[] kService = hmacSha256(kRegion, "ssm");
        byte[] kSigning = hmacSha256(kService, "aws4_request");
        String signature = HexFormat.of().formatHex(hmacSha256(kSigning, stringToSign));

        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host + "/"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", contentType)
                .header("X-Amz-Date", amzDate)
                .header("X-Amz-Target", target)
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (sessionToken != null && !sessionToken.isBlank()) {
            builder.header("X-Amz-Security-Token", sessionToken);
        }
        return builder.build();
    }

    static String extrairValor(String corpoJson, String nomeParametro) {
        try {
            JsonNode raiz = MAPPER.readTree(corpoJson);
            String valor = raiz.path("Parameter").path("Value").asText(null);
            if (valor == null || valor.isBlank()) {
                throw new IllegalStateException("Parâmetro SSM '" + nomeParametro + "' veio vazio.");
            }
            return valor;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Resposta inválida do SSM para '" + nomeParametro + "'.", e);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha SHA-256", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Falha HmacSHA256", e);
        }
    }
}
