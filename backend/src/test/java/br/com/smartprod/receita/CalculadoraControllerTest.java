package br.com.smartprod.receita;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
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

/** Calculadora (RF006, RF007) de ponta a ponta, no formato que a tela da calculadora usa. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "2", roles = "FUNCIONARIO")
class CalculadoraControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InsumoRepository insumoRepository;
    @Autowired
    private FichaTecnicaRepository fichaRepository;

    private Long fichaId;
    private Insumo farinha;
    private Insumo agua;

    @BeforeEach
    void criarFicha() {
        farinha = insumoRepository.save(new Insumo("Farinha calc", UnidadeMedida.G, new BigDecimal("0.0065")));
        agua = insumoRepository.save(new Insumo("Água calc", UnidadeMedida.ML, new BigDecimal("0.0010")));
        FichaTecnica ficha = new FichaTecnica("Pão calc", 50);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, farinha, new BigDecimal("1000"), true));
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, agua, new BigDecimal("700"), false));
        fichaId = fichaRepository.saveAndFlush(ficha).getId();
    }

    @Test
    void funcionarioCalculaPorDemandaSemVerCusto() throws Exception {
        mockMvc.perform(get("/api/fichas-tecnicas/" + fichaId + "/calculo?unidades=120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fichaId").value(fichaId))
                .andExpect(jsonPath("$.unidades").value(120))
                .andExpect(jsonPath("$.fator").value(2.4))
                .andExpect(jsonPath("$.ingredientes[0].nome").value("Farinha calc"))
                .andExpect(jsonPath("$.ingredientes[0].unidadeMedida").value("G"))
                .andExpect(jsonPath("$.ingredientes[0].quantidade").value(2400.0))
                .andExpect(jsonPath("$.ingredientes[1].percentualPadeiro").value(70.0))
                .andExpect(content().string(not(containsString("custo"))));
    }

    @Test
    void funcionarioCalculaPeloEstoque() throws Exception {
        mockMvc.perform(post("/api/fichas-tecnicas/" + fichaId + "/calculo-por-estoque")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"estoque":[{"insumoId":%d,"quantidade":5000},{"insumoId":%d,"quantidade":3000}]}
                        """.formatted(farinha.getId(), agua.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadesPossiveis").value(214))
                .andExpect(jsonPath("$.ingredienteLimitante.insumoId").value(agua.getId()))
                .andExpect(jsonPath("$.ingredienteLimitante.nome").value("Água calc"))
                .andExpect(jsonPath("$.ingredientes[0].quantidade").value(4280.0));
    }

    @Test
    void errosComMensagemEmPortugues() throws Exception {
        mockMvc.perform(get("/api/fichas-tecnicas/" + fichaId + "/calculo?unidades=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Informe quantas unidades produzir (a partir de 1)."));
        mockMvc.perform(get("/api/fichas-tecnicas/999999/calculo?unidades=10"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/fichas-tecnicas/" + fichaId + "/calculo-por-estoque")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"estoque":[{"insumoId":%d,"quantidade":-1}]}
                        """.formatted(farinha.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['estoque[0].quantidade']").exists());
    }
}
