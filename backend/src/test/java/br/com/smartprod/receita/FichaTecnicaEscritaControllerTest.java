package br.com.smartprod.receita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.jayway.jsonpath.JsonPath;

import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.InsumoRepository;
import br.com.smartprod.insumo.UnidadeMedida;
import jakarta.persistence.EntityManager;

/** Cadastro e edição de fichas técnicas (RF005) de ponta a ponta, no formato que a tela de fichas envia. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "1", roles = "GERENTE")
class FichaTecnicaEscritaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InsumoRepository insumoRepository;
    @Autowired
    private FichaTecnicaRepository fichaRepository;
    @Autowired
    private EntityManager entityManager;

    private Insumo farinha;
    private Insumo agua;
    private Insumo sal;

    @BeforeEach
    void criarInsumos() {
        farinha = insumoRepository.save(new Insumo("Farinha escrita", UnidadeMedida.KG, new BigDecimal("6.50")));
        agua = insumoRepository.save(new Insumo("Água escrita", UnidadeMedida.ML, new BigDecimal("0.0010")));
        sal = insumoRepository.save(new Insumo("Sal escrita", UnidadeMedida.G, new BigDecimal("0.0030")));
    }

    @Test
    void gerenteCadastraEAFichaApareceNaLista() throws Exception {
        String corpo = mockMvc.perform(post("/api/fichas-tecnicas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Pão francês escrita","rendimentoPadrao":50,
                 "ingredientes":[{"insumoId":%d,"quantidadeBase":1,"ehFarinha":true},
                                 {"insumoId":%d,"quantidadeBase":700,"ehFarinha":false}],
                 "passos":[{"ordem":1,"etapa":"Mistura","descricao":"Misture tudo.","duracaoSegundos":600},
                           {"ordem":2,"etapa":"Forno","descricao":"Asse.","duracaoSegundos":null}]}
                """.formatted(farinha.getId(), agua.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/fichas-tecnicas/")))
                .andExpect(jsonPath("$.nome").value("Pão francês escrita"))
                .andExpect(jsonPath("$.ingredientes[0].percentualPadeiro").value(100.0)) // 1 kg = 1000 g
                .andExpect(jsonPath("$.ingredientes[1].percentualPadeiro").value(70.0))
                .andExpect(jsonPath("$.passos[1].duracaoSegundos").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(corpo, "$.id");

        mockMvc.perform(get("/api/fichas-tecnicas")).andExpect(jsonPath("$[*].nome", hasItem("Pão francês escrita")));
        mockMvc.perform(get("/api/fichas-tecnicas/" + id)).andExpect(jsonPath("$.passos.length()").value(2));
    }

    @Test
    void edicaoTrocaIngredientesEPassosEApagaOsAntigos() throws Exception {
        Long id = criarFicha();

        mockMvc.perform(put("/api/fichas-tecnicas/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Pão de sal","rendimentoPadrao":20,
                 "ingredientes":[{"insumoId":%d,"quantidadeBase":2,"ehFarinha":true},
                                 {"insumoId":%d,"quantidadeBase":40,"ehFarinha":false}],
                 "passos":[{"ordem":1,"etapa":"Sova","descricao":"Sove.","duracaoSegundos":300}]}
                """.formatted(farinha.getId(), sal.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Pão de sal"))
                .andExpect(jsonPath("$.ingredientes[*].insumoNome", hasItem("Sal escrita")))
                .andExpect(jsonPath("$.passos.length()").value(1));

        entityManager.flush();
        entityManager.clear();
        FichaTecnica salva = fichaRepository.findById(id).orElseThrow();
        assertThat(salva.getIngredientes()).hasSize(2);
        assertThat(salva.getPassos()).extracting(PassoPreparo::getEtapa).containsExactly("Sova");
        Long restantes = entityManager.createQuery(
                "SELECT COUNT(i) FROM FichaIngrediente i WHERE i.fichaTecnica.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        assertThat(restantes).isEqualTo(2); // a água saiu de verdade do banco
    }

    @Test
    void dadosInvalidosDao400ComOsCampos() throws Exception {
        mockMvc.perform(post("/api/fichas-tecnicas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":" ","rendimentoPadrao":0,"ingredientes":[],"passos":[]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.rendimentoPadrao").exists())
                .andExpect(jsonPath("$.campos.ingredientes").exists());

        mockMvc.perform(post("/api/fichas-tecnicas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Pão","rendimentoPadrao":10,"ingredientes":[{"insumoId":%d,"quantidadeBase":0,"ehFarinha":true}],"passos":[]}
                """.formatted(farinha.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['ingredientes[0].quantidadeBase']").exists());
    }

    @Test
    void insumoInativoNaoEntraEmFichaNova() throws Exception {
        farinha.inativar();

        mockMvc.perform(post("/api/fichas-tecnicas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Pão","rendimentoPadrao":10,"ingredientes":[{"insumoId":%d,"quantidadeBase":1,"ehFarinha":true}],"passos":[]}
                """.formatted(farinha.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O insumo \"Farinha escrita\" está inativo e não pode entrar na ficha."));
    }

    @Test
    @WithMockUser(username = "2", roles = "FUNCIONARIO")
    void funcionarioNaoCadastraNemEdita() throws Exception {
        String corpo = """
                {"nome":"Pão","rendimentoPadrao":10,"ingredientes":[{"insumoId":%d,"quantidadeBase":1,"ehFarinha":true}],"passos":[]}
                """.formatted(farinha.getId());
        mockMvc.perform(post("/api/fichas-tecnicas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/fichas-tecnicas/" + criarFicha()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarFichaInexistenteDa404() throws Exception {
        mockMvc.perform(put("/api/fichas-tecnicas/999999").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Pão","rendimentoPadrao":10,"ingredientes":[{"insumoId":%d,"quantidadeBase":1,"ehFarinha":true}],"passos":[]}
                """.formatted(farinha.getId())))
                .andExpect(status().isNotFound());
    }

    private Long criarFicha() {
        FichaTecnica ficha = new FichaTecnica("Pão escrita", 50);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, farinha, BigDecimal.ONE, true));
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, agua, new BigDecimal("700"), false));
        ficha.adicionarPasso(new PassoPreparo(ficha, 1, "Mistura", "Misture.", 600));
        return fichaRepository.saveAndFlush(ficha).getId();
    }
}
