package br.com.smartprod.receita;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.InsumoRepository;
import br.com.smartprod.insumo.UnidadeMedida;
import jakarta.persistence.EntityManager;

/** Calculadora da ficha técnica (RF007) de ponta a ponta. Ninguém vê custos nesta resposta (RNF-S002). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // cada teste desfaz o que criou
class CalculoFichaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FichaTecnicaRepository fichaTecnicaRepository;
    @Autowired
    private InsumoRepository insumoRepository;
    @Autowired
    private EntityManager entityManager;

    // ---- acesso

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/fichas-tecnicas/1/calculo?unidades=10")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/fichas-tecnicas/1/calculo-por-estoque")
                .contentType(MediaType.APPLICATION_JSON).content("{\"estoque\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- por demanda

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteRecalculaPelaQuantidadeDeUnidades() throws Exception {
        FichaTecnica ficha = salvarFicha();

        mockMvc.perform(get("/api/fichas-tecnicas/" + ficha.getId() + "/calculo?unidades=120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fichaId").value(ficha.getId().intValue()))
                .andExpect(jsonPath("$.unidades").value(120))
                .andExpect(jsonPath("$.fator").value(2.4))
                .andExpect(jsonPath("$.ingredientes[0].nome").value("Farinha de trigo"))
                .andExpect(jsonPath("$.ingredientes[0].unidadeMedida").value("G"))
                .andExpect(jsonPath("$.ingredientes[0].quantidade").value(2400.0))
                .andExpect(jsonPath("$.ingredientes[0].percentualPadeiro").value(100.0))
                .andExpect(jsonPath("$.ingredientes[1].nome").value("Água"))
                .andExpect(jsonPath("$.ingredientes[1].quantidade").value(1680.0))
                .andExpect(jsonPath("$.ingredientes[1].percentualPadeiro").value(70.0));
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void funcionarioTambemCalculaESemVerCustos() throws Exception {
        FichaTecnica ficha = salvarFicha();

        mockMvc.perform(get("/api/fichas-tecnicas/" + ficha.getId() + "/calculo?unidades=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientes[0].quantidade").value(2000.0))
                .andExpect(content().string(not(containsString("custo"))));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void unidadesInvalidasRetornam400() throws Exception {
        FichaTecnica ficha = salvarFicha();
        String base = "/api/fichas-tecnicas/" + ficha.getId() + "/calculo";

        mockMvc.perform(get(base + "?unidades=0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base + "?unidades=-5")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base + "?unidades=abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void fichaInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/fichas-tecnicas/999999999/calculo?unidades=10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ficha técnica não encontrada."));
        mockMvc.perform(post("/api/fichas-tecnicas/999999999/calculo-por-estoque")
                .contentType(MediaType.APPLICATION_JSON).content("{\"estoque\":[]}"))
                .andExpect(status().isNotFound());
    }

    // ---- por estoque

    @Test
    @WithMockUser(roles = "GERENTE")
    void devolveAsUnidadesPossiveisEOIngredienteLimitante() throws Exception {
        FichaTecnica ficha = salvarFicha();
        Long idFarinha = ficha.getIngredientes().get(0).getInsumo().getId();
        Long idAgua = ficha.getIngredientes().get(1).getInsumo().getId();

        mockMvc.perform(post("/api/fichas-tecnicas/" + ficha.getId() + "/calculo-por-estoque")
                .contentType(MediaType.APPLICATION_JSON)
                .content(estoque(idFarinha, "5000", idAgua, "10000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadesPossiveis").value(250))
                .andExpect(jsonPath("$.ingredienteLimitante.insumoId").value(idFarinha.intValue()))
                .andExpect(jsonPath("$.ingredienteLimitante.nome").value("Farinha de trigo"))
                .andExpect(jsonPath("$.ingredientes[0].quantidade").value(5000.0))
                .andExpect(jsonPath("$.ingredientes[1].quantidade").value(3500.0));
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void insumoFaltandoNoEstoqueZeraAsUnidades() throws Exception {
        FichaTecnica ficha = salvarFicha();
        Long idFarinha = ficha.getIngredientes().get(0).getInsumo().getId();

        mockMvc.perform(post("/api/fichas-tecnicas/" + ficha.getId() + "/calculo-por-estoque")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estoque\":[{\"insumoId\":" + idFarinha + ",\"quantidade\":5000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadesPossiveis").value(0))
                .andExpect(jsonPath("$.ingredienteLimitante.nome").value("Água"))
                .andExpect(content().string(not(containsString("custo"))));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void corpoInvalidoRetorna400() throws Exception {
        FichaTecnica ficha = salvarFicha();
        String url = "/api/fichas-tecnicas/" + ficha.getId() + "/calculo-por-estoque";

        corpoInvalido(url, "{}");
        corpoInvalido(url, "{\"estoque\":[{\"quantidade\":10}]}");
        corpoInvalido(url, "{\"estoque\":[{\"insumoId\":1}]}");
        corpoInvalido(url, "{\"estoque\":[{\"insumoId\":1,\"quantidade\":-1}]}");
    }

    // ---- apoio

    private void corpoInvalido(String url, String corpo) throws Exception {
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest());
    }

    private String estoque(Long idFarinha, String quantidadeFarinha, Long idAgua, String quantidadeAgua) {
        return "{\"estoque\":["
                + "{\"insumoId\":" + idFarinha + ",\"quantidade\":" + quantidadeFarinha + "},"
                + "{\"insumoId\":" + idAgua + ",\"quantidade\":" + quantidadeAgua + "}]}";
    }

    /** Cria uma ficha de 50 unidades com 1000 g de farinha e 700 ml de água. */
    private FichaTecnica salvarFicha() {
        Insumo farinha = insumoRepository.saveAndFlush(
                new Insumo("Farinha de trigo", UnidadeMedida.G, new BigDecimal("0.0065")));
        Insumo agua = insumoRepository.saveAndFlush(
                new Insumo("Água", UnidadeMedida.ML, new BigDecimal("0.0001")));

        FichaTecnica ficha = new FichaTecnica("Pão francês", 50);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, farinha, new BigDecimal("1000"), true));
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, agua, new BigDecimal("700"), false));

        FichaTecnica salva = fichaTecnicaRepository.saveAndFlush(ficha);
        entityManager.clear(); // força o sistema a reler do banco, como numa chamada de verdade
        return salva;
    }
}