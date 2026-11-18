package br.com.smartprod.dashboard;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.dashboard.DashboardConsultas.PerdaPorFicha;
import br.com.smartprod.dashboard.DashboardConsultas.Rendimento;
import br.com.smartprod.dashboard.dto.DesperdicioResponse;
import br.com.smartprod.dashboard.dto.ProdutoDesperdicioResponse;
import br.com.smartprod.dashboard.dto.RendimentoResponse;
import br.com.smartprod.dashboard.dto.StatusOpsResponse;
import br.com.smartprod.producao.StatusOrdemProducao;

/**
 * Indicadores do dashboard do Gerente (RF012 a RF015). Os dias são do fuso {@code America/Sao_Paulo}: o
 * dia 18 vai de 18/11 00:00 a 19/11 00:00 no horário de São Paulo.
 */
@Service
public class DashboardService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    static final int TAMANHO_DO_RANKING = 3;

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final DashboardConsultas consultas;
    private final Clock relogio;

    @Autowired
    public DashboardService(DashboardConsultas consultas) {
        this(consultas, Clock.systemUTC());
    }

    DashboardService(DashboardConsultas consultas, Clock relogio) {
        this.consultas = consultas;
        this.relogio = relogio;
    }

    /** Hoje no fuso de São Paulo. */
    public LocalDate hoje() {
        return LocalDate.now(relogio.withZone(FUSO));
    }

    /** RF012: previsto = soma das OPs com entrega no dia; produzido = soma das que já foram concluídas. */
    @Transactional(readOnly = true)
    public RendimentoResponse rendimento(LocalDate dia) {
        Rendimento soma = consultas.rendimento(inicio(dia), inicio(dia.plusDays(1)));
        BigDecimal percentual = soma.previsto() == 0
                ? null
                : BigDecimal.valueOf(soma.produzido()).multiply(CEM)
                        .divide(BigDecimal.valueOf(soma.previsto()), 1, RoundingMode.HALF_UP);
        return new RendimentoResponse(soma.previsto(), soma.produzido(), percentual);
    }

    /** RF013: peso perdido no período (de e até inclusive) e o valor em R$ pelo custo por kg de cada receita. */
    @Transactional(readOnly = true)
    public DesperdicioResponse desperdicio(LocalDate de, LocalDate ate) {
        List<ProdutoDesperdicioResponse> produtos = perdasComValor(de, ate);
        BigDecimal totalKg = produtos.stream().map(ProdutoDesperdicioResponse::totalKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3, RoundingMode.HALF_UP);
        List<BigDecimal> valores = produtos.stream().map(ProdutoDesperdicioResponse::totalReais)
                .filter(Objects::nonNull).toList();
        BigDecimal totalReais = !produtos.isEmpty() && valores.isEmpty()
                ? null
                : valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new DesperdicioResponse(totalKg, totalReais);
    }

    /** RF014: os 3 produtos com mais kg perdidos no período. */
    @Transactional(readOnly = true)
    public List<ProdutoDesperdicioResponse> ranking(LocalDate de, LocalDate ate) {
        return perdasComValor(de, ate).stream()
                .sorted(Comparator.comparing(ProdutoDesperdicioResponse::totalKg).reversed()
                        .thenComparing(ProdutoDesperdicioResponse::produto))
                .limit(TAMANHO_DO_RANKING)
                .toList();
    }

    /** RF015: pendentes e em andamento contam todas; concluídas, só as de hoje (igual ao Kanban). */
    @Transactional(readOnly = true)
    public StatusOpsResponse statusOps() {
        LocalDate hoje = hoje();
        Map<StatusOrdemProducao, Long> contagem = consultas.contarOps(inicio(hoje), inicio(hoje.plusDays(1)));
        return new StatusOpsResponse(contagem.get(StatusOrdemProducao.PENDENTE),
                contagem.get(StatusOrdemProducao.EM_ANDAMENTO), contagem.get(StatusOrdemProducao.CONCLUIDA));
    }

    private List<ProdutoDesperdicioResponse> perdasComValor(LocalDate de, LocalDate ate) {
        if (de.isAfter(ate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A data inicial do período não pode ser depois da final.");
        }
        List<PerdaPorFicha> perdas = consultas.perdasPorFicha(inicio(de), inicio(ate.plusDays(1)));
        Map<Long, List<CustoPorKg.Ingrediente>> ingredientesPorFicha = consultas
                .ingredientes(perdas.stream().map(PerdaPorFicha::fichaId).toList()).stream()
                .collect(groupingBy(DashboardConsultas.IngredienteDaFicha::fichaId, mapping(
                        i -> new CustoPorKg.Ingrediente(i.quantidade(), i.unidade(), i.custoUnitario()), toList())));

        return perdas.stream().map(perda -> {
            BigDecimal custoPorKg = CustoPorKg.calcular(ingredientesPorFicha.getOrDefault(perda.fichaId(), List.of()));
            BigDecimal kg = perda.pesoKg().setScale(3, RoundingMode.HALF_UP);
            BigDecimal reais = custoPorKg == null ? null : kg.multiply(custoPorKg).setScale(2, RoundingMode.HALF_UP);
            return new ProdutoDesperdicioResponse(perda.fichaId(), perda.produto(), kg, reais);
        }).toList();
    }

    private static Instant inicio(LocalDate dia) {
        return dia.atStartOfDay(FUSO).toInstant();
    }
}
