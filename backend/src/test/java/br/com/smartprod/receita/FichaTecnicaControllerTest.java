package br.com.smartprod.receita;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.smartprod.auth.TokenService;
import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.InsumoRepository;
import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;

/** Consulta de fichas técnicas (RF005, RF006) de ponta a ponta. Ninguém vê custos nesta resposta (RNF-S002). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // cada teste desfaz o que criou
class FichaTecnicaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FichaTecnicaRepository fichaTecnicaRepository;
    @Autowired
    private InsumoRepository insumoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private EntityManager entityManager;

    // ---- acesso

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/fichas-tecnicas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/fichas-tecnicas/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void funcionarioComTokenRealListaSemCusto() throws Exception {
        salvarFicha();
        Usuario funcionario = usuarioRepository.saveAndFlush(
                new Usuario("Beto", "beto.ficha@padaria.com", "hash", Perfil.FUNCIONARIO));
        String token = tokenService.emitir(funcionario).valor();

        mockMvc.perform(get("/api/fichas-tecnicas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("Pão francês")))
                .andExpect(content().string(not(containsString("custo"))));
    }

    // ---- lista

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteListaAsFichas() throws Exception {
        salvarFicha();

        mockMvc.perform(get("/api/fichas-tecnicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("Pão francês")))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].rendimentoPadrao").isNumber());
    }

    // ---- custos (só Gerente)

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteVeOCustoDaReceitaDaUnidadeEDoKg() throws Exception {
        FichaTecnica ficha = salvarFicha(); // 1000 g × 0,0065 + 700 ml × 0,0001 = R$ 6,57 para 50 un. e 1,7 kg
        String daFicha = "$[?(@.fichaId == " + ficha.getId() + ")]";

        mockMvc.perform(get("/api/fichas-tecnicas/custos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(daFicha + ".nome", hasItem("Pão francês")))
                .andExpect(jsonPath(daFicha + ".custoReceita", hasItem(6.57)))
                .andExpect(jsonPath(daFicha + ".custoUnidade", hasItem(0.1314)))
                .andExpect(jsonPath(daFicha + ".custoPorKg", hasItem(3.86)));
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void funcionarioNaoVeCustos() throws Exception {
        mockMvc.perform(get("/api/fichas-tecnicas/custos")).andExpect(status().isForbidden());
    }

    // ---- consulta por id

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void funcionarioConsultaFichaCompletaSemCusto() throws Exception {
        FichaTecnica ficha = salvarFicha();

        mockMvc.perform(get("/api/fichas-tecnicas/" + ficha.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Pão francês"))
                .andExpect(jsonPath("$.rendimentoPadrao").value(50))
                .andExpect(jsonPath("$.ingredientes", hasSize(2)))
                .andExpect(jsonPath("$.passos", hasSize(2)))
                .andExpect(content().string(not(containsString("custo"))));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void devolveIngredientesComDadosDoInsumo() throws Exception {
        FichaTecnica ficha = salvarFicha();

        mockMvc.perform(get("/api/fichas-tecnicas/" + ficha.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientes[0].insumoId").isNumber())
                .andExpect(jsonPath("$.ingredientes[0].insumoNome").value("Farinha de trigo"))
                .andExpect(jsonPath("$.ingredientes[0].unidadeMedida").value("G"))
                .andExpect(jsonPath("$.ingredientes[0].quantidadeBase").value(1000.0))
                .andExpect(jsonPath("$.ingredientes[0].ehFarinha").value(true))
                .andExpect(jsonPath("$.ingredientes[1].insumoNome").value("Água"))
                .andExpect(jsonPath("$.ingredientes[1].ehFarinha").value(false));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void devolveOsPassosNaOrdemCerta() throws Exception {
        // os passos foram salvos fora de ordem (2 e depois 1)
        FichaTecnica ficha = salvarFicha();

        mockMvc.perform(get("/api/fichas-tecnicas/" + ficha.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passos[0].ordem").value(1))
                .andExpect(jsonPath("$.passos[0].etapa").value("Mistura"))
                .andExpect(jsonPath("$.passos[0].duracaoSegundos").value(600))
                .andExpect(jsonPath("$.passos[1].ordem").value(2))
                .andExpect(jsonPath("$.passos[1].etapa").value("Descanso"));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void devolve404QuandoAFichaNaoExiste() throws Exception {
        mockMvc.perform(get("/api/fichas-tecnicas/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ficha técnica não encontrada."));
    }

    // ---- porcentagem do padeiro (RF006)

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void incluiAPorcentagemDoPadeiroDeCadaIngrediente() throws Exception {
        FichaTecnica ficha = salvarFicha();

        mockMvc.perform(get("/api/fichas-tecnicas/" + ficha.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientes[0].percentualPadeiro").value(100.0))
                .andExpect(jsonPath("$.ingredientes[1].percentualPadeiro").value(70.0));
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void fichaSemFarinhaAindaAbreEDevolvePercentualNulo() throws Exception {
        Insumo leite = insumoRepository.saveAndFlush(
                new Insumo("Leite condensado", UnidadeMedida.G, new BigDecimal("0.02")));
        FichaTecnica ficha = new FichaTecnica("Brigadeiro", 40);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, leite, new BigDecimal("395"), false));
        FichaTecnica salva = fichaTecnicaRepository.saveAndFlush(ficha);
        entityManager.clear();

        mockMvc.perform(get("/api/fichas-tecnicas/" + salva.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Brigadeiro"))
                .andExpect(jsonPath("$.ingredientes[0].insumoNome").value("Leite condensado"))
                .andExpect(jsonPath("$.ingredientes[0].percentualPadeiro").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void convertePesosEVolumesAntesDeCalcularAPorcentagem() throws Exception {
        Insumo farinha = insumoRepository.saveAndFlush(
                new Insumo("Farinha de trigo", UnidadeMedida.KG, new BigDecimal("6.50")));
        Insumo sal = insumoRepository.saveAndFlush(new Insumo("Sal", UnidadeMedida.G, new BigDecimal("0.01")));
        Insumo ovo = insumoRepository.saveAndFlush(new Insumo("Ovo", UnidadeMedida.UN, new BigDecimal("0.80")));
        FichaTecnica ficha = new FichaTecnica("Pão", 50);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, farinha, new BigDecimal("5"), true));
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, sal, new BigDecimal("100"), false));
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, ovo, new BigDecimal("2"), false));
        FichaTecnica salva = fichaTecnicaRepository.saveAndFlush(ficha);
        entityManager.clear();

        mockMvc.perform(get("/api/fichas-tecnicas/" + salva.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientes[0].percentualPadeiro").value(100.0))
                .andExpect(jsonPath("$.ingredientes[1].percentualPadeiro").value(2.0))
                .andExpect(jsonPath("$.ingredientes[2].percentualPadeiro").value(nullValue()));
    }

    // ---- apoio

    /** Cria uma ficha com 2 ingredientes e 2 passos (salvos fora de ordem de propósito). */
    private FichaTecnica salvarFicha() {
        Insumo farinha = insumoRepository.saveAndFlush(
                new Insumo("Farinha de trigo", UnidadeMedida.G, new BigDecimal("0.0065")));
        Insumo agua = insumoRepository.saveAndFlush(
                new Insumo("Água", UnidadeMedida.ML, new BigDecimal("0.0001")));

        FichaTecnica ficha = new FichaTecnica("Pão francês", 50);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, farinha, new BigDecimal("1000"), true));
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, agua, new BigDecimal("700"), false));
        ficha.adicionarPasso(new PassoPreparo(ficha, 2, "Descanso", "Deixe a massa descansar", null));
        ficha.adicionarPasso(new PassoPreparo(ficha, 1, "Mistura", "Misture tudo", 600));

        FichaTecnica salva = fichaTecnicaRepository.saveAndFlush(ficha);
        entityManager.clear(); // força o sistema a reler do banco, como numa chamada de verdade
        return salva;
    }
}