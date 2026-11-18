package br.com.smartprod.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.smartprod.dashboard.DashboardService;
import br.com.smartprod.dashboard.dto.DesperdicioResponse;
import br.com.smartprod.dashboard.dto.ProdutoDesperdicioResponse;
import br.com.smartprod.dashboard.dto.StatusOpsResponse;
import br.com.smartprod.receita.FichaTecnicaService;
import br.com.smartprod.receita.dto.FichaTecnicaResponse;

/**
 * Perfil {@code demo} com banco H2 próprio: os dados são criados na subida, são válidos para a API e o
 * dashboard, e rodar de novo não duplica nada.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:smartprod_demo;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "demo"})
class DadosDemonstracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DadosDemonstracao dados;

    @Autowired
    private DashboardService dashboard;

    @Autowired
    private FichaTecnicaService fichaService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void criaOsCadastrosNaSubida() {
        assertThat(contar("SELECT COUNT(*) FROM insumos")).isEqualTo(14);
        assertThat(contar("SELECT COUNT(*) FROM insumos WHERE ativo = FALSE")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM fichas_tecnicas")).isEqualTo(4);
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE perfil = 'FUNCIONARIO'")).isEqualTo(4);
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE perfil = 'FUNCIONARIO' AND ativo = TRUE")).isEqualTo(3);
    }

    @Test
    void criaHistoricoEAsOpsDeHoje() {
        // 13 dias × 4 OPs + o bolo nos 6 dias pares + 6 OPs de hoje
        assertThat(contar("SELECT COUNT(*) FROM ordens_producao")).isEqualTo(13 * 4 + 6 + 6);
        assertThat(contar("SELECT COUNT(*) FROM registros_perda")).isGreaterThan(20);

        StatusOpsResponse status = dashboard.statusOps();
        assertThat(status.pendentes()).isEqualTo(3);
        assertThat(status.emAndamento()).isEqualTo(1);
        assertThat(status.concluidas()).isPositive();
    }

    @Test
    void dashboardDaSemanaTemDesperdicioETop3() {
        LocalDate hoje = dashboard.hoje();

        DesperdicioResponse semana = dashboard.desperdicio(hoje.minusDays(6), hoje);
        List<ProdutoDesperdicioResponse> ranking = dashboard.ranking(hoje.minusDays(6), hoje);

        assertThat(semana.totalKg()).isPositive();
        assertThat(semana.totalReais()).isPositive();
        assertThat(ranking).hasSize(3);
    }

    @Test
    void fichasSaemPelaApiComPorcentagemDoPadeiro() {
        Long id = jdbc.queryForObject("SELECT id FROM fichas_tecnicas WHERE nome = 'Pão francês'", Long.class);

        FichaTecnicaResponse ficha = fichaService.buscar(id);

        assertThat(ficha.rendimentoPadrao()).isEqualTo(30);
        assertThat(ficha.passos()).hasSize(6);
        assertThat(ficha.ingredientes())
                .filteredOn(i -> i.insumoNome().equals("Água"))
                .singleElement()
                .satisfies(agua -> assertThat(agua.percentualPadeiro()).isEqualByComparingTo("60"));
    }

    @Test
    void perdasTemPesoCoerenteComAsUnidades() {
        // Pão francês: 1.640 kg de massa ÷ 30 pães ≈ 0,055 kg por pão
        BigDecimal pesoDeSeisPaes = jdbc.queryForObject("""
                SELECT p.peso_kg FROM registros_perda p
                JOIN ordens_producao o ON o.id = p.ordem_producao_id
                JOIN fichas_tecnicas f ON f.id = o.ficha_tecnica_id
                WHERE f.nome = 'Pão francês' AND p.unidades = 6
                FETCH FIRST 1 ROWS ONLY
                """, BigDecimal.class);

        assertThat(pesoDeSeisPaes).isEqualByComparingTo("0.328");
    }

    @Test
    void rodarDeNovoNaoDuplica() {
        long ops = contar("SELECT COUNT(*) FROM ordens_producao");
        long usuarios = contar("SELECT COUNT(*) FROM usuarios");

        dados.criar();

        assertThat(contar("SELECT COUNT(*) FROM ordens_producao")).isEqualTo(ops);
        assertThat(contar("SELECT COUNT(*) FROM usuarios")).isEqualTo(usuarios);
        assertThat(contar("SELECT COUNT(*) FROM fichas_tecnicas")).isEqualTo(4);
    }

    @Test
    void funcionarioDeDemonstracaoConsegueEntrar() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"beto@smartprod.dev","senha":"%s"}
                        """.formatted(DadosDemonstracao.SENHA_FUNCIONARIOS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("FUNCIONARIO"));
    }

    private long contar(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
