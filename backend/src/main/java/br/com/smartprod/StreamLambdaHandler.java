package br.com.smartprod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

/**
 * Entrypoint AWS Lambda (HTTP API v2 / Lambda Function URL) para o SmartProd.
 *
 * <p>Usa inicialização lazy thread-safe na primeira invocação para evitar que o limite de 10 s da
 * fase INIT da Lambda interrompa a subida do Spring Boot + Flyway quando o Aurora Serverless v2
 * estiver acordando de auto-pause (0 ACU), e força UTF-8 como charset padrão do container para
 * evitar mojibake em respostas sem parâmetro {@code charset}.
 */
public class StreamLambdaHandler implements RequestStreamHandler {

    private static volatile SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> handler;

    private static SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> getHandler() {
        SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> current = handler;
        if (current == null) {
            synchronized (StreamLambdaHandler.class) {
                current = handler;
                if (current == null) {
                    try {
                        LambdaContainerHandler.getContainerConfig()
                                .setDefaultContentCharset(StandardCharsets.UTF_8.name());
                        current = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(SmartProdApplication.class);
                        handler = current;
                    } catch (ContainerInitializationException e) {
                        throw new RuntimeException("Falha ao inicializar o Spring Boot na Lambda", e);
                    }
                }
            }
        }
        return current;
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        getHandler().proxyStream(inputStream, outputStream, context);
    }
}
