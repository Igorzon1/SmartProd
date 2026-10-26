package br.com.smartprod;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SmartProdApplicationTests {

    @Test
    void contextoSobeComMigracoesEValidacaoDoEsquema() {
        // Falha se o Flyway quebrar ou se as entidades divergirem das migrações (ddl-auto=validate).
    }
}
