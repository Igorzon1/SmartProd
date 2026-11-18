package br.com.smartprod.dashboard;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.smartprod.dashboard.dto.DesperdicioResponse;
import br.com.smartprod.dashboard.dto.ProdutoDesperdicioResponse;
import br.com.smartprod.dashboard.dto.RendimentoResponse;
import br.com.smartprod.dashboard.dto.StatusOpsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Dashboard do Gerente (RF012 a RF015). Tudo aqui tem valor em R$ ou é gerencial: só GERENTE (RNF-S002).
 * Datas em AAAA-MM-DD, no dia de São Paulo. Sem período, vale a última semana (hoje e os 6 dias antes).
 */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasRole('GERENTE')")
@Tag(name = "Dashboard", description = "Indicadores do Gerente (RF012 a RF015) — só Gerente")
public class DashboardController {

    private static final int DIAS_DO_PERIODO_PADRAO = 7;

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/rendimento")
    @Operation(summary = "Rendimento do dia: produzido × previsto (padrão: hoje)")
    public RendimentoResponse rendimento(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return dashboardService.rendimento(data != null ? data : dashboardService.hoje());
    }

    @GetMapping("/desperdicio")
    @Operation(summary = "Desperdício no período, em kg e em R$ (de e até inclusive)")
    public DesperdicioResponse desperdicio(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        LocalDate fim = ate != null ? ate : dashboardService.hoje();
        return dashboardService.desperdicio(de != null ? de : inicioPadrao(fim), fim);
    }

    @GetMapping("/ranking-desperdicio")
    @Operation(summary = "Os 3 produtos com mais kg perdidos no período")
    public List<ProdutoDesperdicioResponse> ranking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        LocalDate fim = ate != null ? ate : dashboardService.hoje();
        return dashboardService.ranking(de != null ? de : inicioPadrao(fim), fim);
    }

    @GetMapping("/status-ops")
    @Operation(summary = "OPs pendentes, em andamento e concluídas hoje")
    public StatusOpsResponse statusOps() {
        return dashboardService.statusOps();
    }

    private static LocalDate inicioPadrao(LocalDate fim) {
        return fim.minusDays(DIAS_DO_PERIODO_PADRAO - 1L);
    }
}
