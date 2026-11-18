package br.com.smartprod.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.producao.StatusOrdemProducao;
import jakarta.persistence.EntityManager;

/**
 * Consultas do dashboard, só de leitura, sobre as tabelas de OPs, perdas e fichas (docs/09: o dashboard
 * lê os dados das outras duplas, mas não grava nada).
 */
@Repository
class DashboardConsultas {

    private final EntityManager em;

    DashboardConsultas(EntityManager em) {
        this.em = em;
    }

    /** Soma prevista e produzida das OPs com entrega no intervalo. */
    record Rendimento(long previsto, long produzido) {
    }

    /** Peso perdido de um produto (ficha) no intervalo. */
    record PerdaPorFicha(Long fichaId, String produto, BigDecimal pesoKg) {
    }

    /** Ingrediente de uma ficha com a unidade e o custo do insumo. */
    record IngredienteDaFicha(Long fichaId, BigDecimal quantidade, UnidadeMedida unidade, BigDecimal custoUnitario) {
    }

    Rendimento rendimento(Instant inicio, Instant fim) {
        Object[] linha = em.createQuery("""
                SELECT COALESCE(SUM(o.quantidadeUnidades), 0),
                       COALESCE(SUM(CASE WHEN o.status = :concluida THEN o.quantidadeProduzida ELSE 0 END), 0)
                FROM OrdemProducao o
                WHERE o.horarioEntrega >= :inicio AND o.horarioEntrega < :fim
                """, Object[].class)
                .setParameter("concluida", StatusOrdemProducao.CONCLUIDA)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getSingleResult();
        return new Rendimento(((Number) linha[0]).longValue(), ((Number) linha[1]).longValue());
    }

    /** Pendentes e em andamento contam todas; concluídas, só as concluídas no intervalo. */
    Map<StatusOrdemProducao, Long> contarOps(Instant inicioConcluidas, Instant fimConcluidas) {
        Map<StatusOrdemProducao, Long> contagem = new EnumMap<>(StatusOrdemProducao.class);
        for (StatusOrdemProducao status : StatusOrdemProducao.values()) {
            contagem.put(status, 0L);
        }
        em.createQuery("""
                SELECT o.status, COUNT(o)
                FROM OrdemProducao o
                WHERE o.status <> :concluida
                   OR (o.concluidaEm >= :inicio AND o.concluidaEm < :fim)
                GROUP BY o.status
                """, Object[].class)
                .setParameter("concluida", StatusOrdemProducao.CONCLUIDA)
                .setParameter("inicio", inicioConcluidas)
                .setParameter("fim", fimConcluidas)
                .getResultList()
                .forEach(linha -> contagem.put((StatusOrdemProducao) linha[0], ((Number) linha[1]).longValue()));
        return contagem;
    }

    List<PerdaPorFicha> perdasPorFicha(Instant inicio, Instant fim) {
        return em.createQuery("""
                SELECT f.id, f.nome, SUM(p.pesoKg)
                FROM RegistroPerda p JOIN p.ordemProducao o JOIN o.fichaTecnica f
                WHERE p.registradoEm >= :inicio AND p.registradoEm < :fim
                GROUP BY f.id, f.nome
                """, Object[].class)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getResultList().stream()
                .map(linha -> new PerdaPorFicha((Long) linha[0], (String) linha[1], (BigDecimal) linha[2]))
                .toList();
    }

    List<IngredienteDaFicha> ingredientes(Collection<Long> fichaIds) {
        if (fichaIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("""
                SELECT i.fichaTecnica.id, i.quantidadeBase, ins.unidadeMedida, ins.custoUnitario
                FROM FichaIngrediente i JOIN i.insumo ins
                WHERE i.fichaTecnica.id IN :ids
                """, Object[].class)
                .setParameter("ids", fichaIds)
                .getResultList().stream()
                .map(linha -> new IngredienteDaFicha((Long) linha[0], (BigDecimal) linha[1],
                        (UnidadeMedida) linha[2], (BigDecimal) linha[3]))
                .toList();
    }
}
