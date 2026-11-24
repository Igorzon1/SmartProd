package br.com.smartprod.config;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.AbstractDataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Configura o DataSource PostgreSQL usando autenticação IAM do Amazon Aurora (SigV4 + TLS).
 *
 * <p>Ativada somente quando {@code app.db.iam-auth=true} (env {@code DB_IAM_AUTH=true}).
 * Gera tokens temporários (validade 15 min, cache local de 10 min) via assinatura AWS SigV4
 * usando as credenciais nativas da execução AWS Lambda (sem depender de JARs extras do AWS SDK).
 */
@Configuration
@ConditionalOnProperty(name = "app.db.iam-auth", havingValue = "true")
public class IamDataSourceConfig {

    private static final Duration TOKEN_CACHE_TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter AMZ_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Bean
    @Primary
    DataSource iamDataSource(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${app.db.iam-region:us-east-2}") String iamRegion) {
        String stripped = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
        URI uri = URI.create(stripped);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;

        DataSource pgIam = new AbstractDataSource() {
            private volatile String cachedToken;
            private volatile Instant tokenExpiresAt = Instant.EPOCH;

            private String freshToken() {
                Instant now = Instant.now();
                if (cachedToken == null || now.isAfter(tokenExpiresAt)) {
                    synchronized (this) {
                        if (cachedToken == null || Instant.now().isAfter(tokenExpiresAt)) {
                            cachedToken = generateRdsAuthToken(host, port, iamRegion, username, Instant.now());
                            tokenExpiresAt = Instant.now().plus(TOKEN_CACHE_TTL);
                        }
                    }
                }
                return cachedToken;
            }

            @Override
            public Connection getConnection() throws SQLException {
                return getConnection(username, null);
            }

            @Override
            public Connection getConnection(String user, String password) throws SQLException {
                Properties props = new Properties();
                props.setProperty("user", user != null ? user : username);
                props.setProperty("password", freshToken());
                props.setProperty("sslmode", "require");
                return DriverManager.getConnection(jdbcUrl, props);
            }
        };

        HikariConfig hikari = new HikariConfig();
        hikari.setDataSource(pgIam);
        hikari.setPoolName("smartprod-aurora-iam");
        hikari.setMaximumPoolSize(3);
        hikari.setMinimumIdle(0);
        hikari.setIdleTimeout(60_000L);
        hikari.setMaxLifetime(540_000L);
        hikari.setConnectionTimeout(45_000L);
        hikari.setValidationTimeout(5_000L);
        return new HikariDataSource(hikari);
    }

    static String generateRdsAuthToken(String host, int port, String region, String dbUser, Instant now) {
        return generateRdsAuthToken(
                host,
                port,
                region,
                dbUser,
                now,
                System.getenv("AWS_ACCESS_KEY_ID"),
                System.getenv("AWS_SECRET_ACCESS_KEY"),
                System.getenv("AWS_SESSION_TOKEN"));
    }

    static String generateRdsAuthToken(String host, int port, String region, String dbUser, Instant now,
            String accessKey, String secretKey, String sessionToken) {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Credenciais AWS (AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY) ausentes para IAM DB Auth");
        }

        String amzDate = AMZ_DATE_FMT.format(now);
        String dateStamp = DATE_STAMP_FMT.format(now);
        String scope = dateStamp + "/" + region + "/rds-db/aws4_request";
        String hostHeader = host + ":" + port;

        Map<String, String> params = new TreeMap<>();
        params.put("Action", "connect");
        params.put("DBUser", dbUser);
        params.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        params.put("X-Amz-Credential", accessKey + "/" + scope);
        params.put("X-Amz-Date", amzDate);
        params.put("X-Amz-Expires", "900");
        if (sessionToken != null && !sessionToken.isBlank()) {
            params.put("X-Amz-Security-Token", sessionToken);
        }
        params.put("X-Amz-SignedHeaders", "host");

        String canonicalQuery = params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

        String canonicalRequest = "GET\n/\n" + canonicalQuery + "\nhost:" + hostHeader + "\n\nhost\n" + EMPTY_SHA256;
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest);

        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, "rds-db");
        byte[] kSigning = hmacSha256(kService, "aws4_request");
        String signature = HexFormat.of().formatHex(hmacSha256(kSigning, stringToSign));

        return hostHeader + "/?" + canonicalQuery + "&X-Amz-Signature=" + signature;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
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
