package br.com.smartprod.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.InsumoRepository;
import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.perda.RegistroPerda;
import br.com.smartprod.perda.RegistroPerdaRepository;
import br.com.smartprod.producao.OrdemProducao;
import br.com.smartprod.producao.OrdemProducaoRepository;
import br.com.smartprod.receita.FichaIngrediente;
import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;

/**
 * Dashboard de ponta a ponta (RF012 a RF015), com as consultas reais no H2. Outros testes deixam OPs e
 * perdas gravadas no banco compartilhado, então cada teste apaga tudo dentro da própria transação, que é
 * desfeita no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "1", roles = "GERENTE")
class DashboardControllerTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.now(FUSO);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RegistroPerdaRepository perdaRepository;
    @Autowired
    private OrdemProducaoRepository ordemRepository;
    @Autowired
    private FichaTecnicaRepository fichaRepository;
    @Autowired
    private InsumoRepository insumoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario chefe;
    private FichaTecnica paoFrances;
    private FichaTecnica brioche;

    @BeforeEach
    void montarCenario() {
        perdaRepository.deleteAll();
        ordemRepository.deleteAll();

        chefe = usuarioRepository.save(new Usuario("Chefe", "chefe.dashboard@padaria.com", "hash", Perfil.GERENTE));
        Insumo farinha = insumoRepository.save(new Insumo("Farinha dashboard", UnidadeMedida.G, new BigDecimal("0.0050")));
        Insumo agua = insumoRepository.save(new Insumo("Água dashboard", UnidadeMedida.ML, new BigDecimal("0.0010")));
        Insumo manteiga = insumoRepository.save(new Insumo("Manteiga dashboard", UnidadeMedida.G, new BigDecimal("0.0300")));

        // Pão francês: R$ 6,00 para 2 kg de massa (R$ 3,00/kg). Brioche: R$ 17,50 para 1 kg.
        paoFrances = ficha("Pão francês dashboard", farinha, "1000", agua, "1000");
        brioche = ficha("Brioche dashboard", farinha, "500", manteiga, "500");

        OrdemProducao concluida = ordem(paoFrances, 120, hojeAoMeioDia());
        concluida.iniciar();
        concluida.concluir(118);
        ordem(paoFrances, 100, hojeAoMeioDia()); // pendente
        OrdemProducao emAndamento = ordem(brioche, 60, hojeAoMeioDia());
        emAndamento.iniciar();
        OrdemProducao deOntem = ordem(paoFrances, 50, hojeAoMeioDia().minusSeconds(86_400));
        deOntem.iniciar();

        perdaRepository.save(new RegistroPerda(deOntem, "Forno", new BigDecimal("2.500"), 10, "Queimou", chefe));
        perdaRepository.save(new RegistroPerda(emAndamento, "Modelagem", new BigDecimal("1.000"), null, "Caiu", chefe));
    }

    @Test
    void rendimentoDoDiaSomaAsOpsComEntregaNoDia() throws Exception {
        mockMvc.perform(get("/api/dashboard/rendimento?data=" + HOJE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previsto").value(280))
                .andExpect(jsonPath("$.produzido").value(118))
                .andExpect(jsonPath("$.rendimentoPercentual").value(42.1));

        mockMvc.perform(get("/api/dashboard/rendimento?data=2030-01-15"))
                .andExpect(jsonPath("$.previsto").value(0))
                .andExpect(jsonPath("$.rendimentoPercentual").doesNotExist());
    }

    @Test
    void desperdicioEmKgEEmReaisPeloCustoPorKgDaReceita() throws Exception {
        mockMvc.perform(get("/api/dashboard/desperdicio?de=" + HOJE + "&ate=" + HOJE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalKg").value(3.5))
                .andExpect(jsonPath("$.totalReais").value(25.0)); // 2,5 kg × 3,00 + 1 kg × 17,50

        mockMvc.perform(get("/api/dashboard/desperdicio?de=2030-01-01&ate=2030-01-31"))
                .andExpect(jsonPath("$.totalKg").value(0))
                .andExpect(jsonPath("$.totalReais").value(0));
    }

    @Test
    void rankingPorKgPerdido() throws Exception {
        mockMvc.perform(get("/api/dashboard/ranking-desperdicio?de=" + HOJE + "&ate=" + HOJE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fichaId").value(paoFrances.getId()))
                .andExpect(jsonPath("$[0].produto").value("Pão francês dashboard"))
                .andExpect(jsonPath("$[0].totalKg").value(2.5))
                .andExpect(jsonPath("$[0].totalReais").value(7.5))
                .andExpect(jsonPath("$[1].fichaId").value(brioche.getId()))
                .andExpect(jsonPath("$[1].totalReais").value(17.5));
    }

    @Test
    void semPeriodoValeAUltimaSemana() throws Exception {
        mockMvc.perform(get("/api/dashboard/desperdicio")).andExpect(jsonPath("$.totalKg").value(3.5));
    }

    @Test
    void statusContaPendentesEmAndamentoEConcluidasDeHoje() throws Exception {
        mockMvc.perform(get("/api/dashboard/status-ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendentes").value(1))
                .andExpect(jsonPath("$.emAndamento").value(2))
                .andExpect(jsonPath("$.concluidas").value(1));
    }

    @Test
    void periodoInvertidoDa400() throws Exception {
        mockMvc.perform(get("/api/dashboard/desperdicio?de=" + HOJE + "&ate=" + HOJE.minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A data inicial do período não pode ser depois da final."));
    }

    @Test
    @WithMockUser(username = "2", roles = "FUNCIONARIO")
    void funcionarioNaoVeODashboard() throws Exception {
        for (String endereco : new String[] { "rendimento", "desperdicio", "ranking-desperdicio", "status-ops" }) {
            mockMvc.perform(get("/api/dashboard/" + endereco)).andExpect(status().isForbidden());
        }
    }

    private FichaTecnica ficha(String nome, Insumo primeiro, String qtdPrimeiro, Insumo segundo, String qtdSegundo) {
        FichaTecnica ficha = new FichaTecnica(nome, 50);
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, primeiro, new BigDecimal(qtdPrimeiro), true));
        ficha.adicionarIngrediente(new FichaIngrediente(ficha, segundo, new BigDecimal(qtdSegundo), false));
        return fichaRepository.save(ficha);
    }

    private OrdemProducao ordem(FichaTecnica ficha, int unidades, Instant entrega) {
        return ordemRepository.save(new OrdemProducao(ficha, unidades, entrega, chefe, chefe));
    }

    private static Instant hojeAoMeioDia() {
        return HOJE.atTime(12, 0).atZone(FUSO).toInstant();
    }
}
