
package br.com.smartprod.producao;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdemProducaoRepository
        extends JpaRepository<OrdemProducao, Long> {

    List<OrdemProducao> findByStatus(StatusOrdemProducao status);

    List<OrdemProducao> findByResponsavelId(Long responsavelId);

    List<OrdemProducao> findAllByOrderByHorarioEntregaAsc();

    List<OrdemProducao> findByStatusOrderByHorarioEntregaAsc(
            StatusOrdemProducao status);

    List<OrdemProducao> findByHorarioEntregaGreaterThanEqualAndHorarioEntregaLessThanOrderByHorarioEntregaAsc(
            Instant inicio,
            Instant fim);

    List<OrdemProducao> findByStatusAndHorarioEntregaGreaterThanEqualAndHorarioEntregaLessThanOrderByHorarioEntregaAsc(
            StatusOrdemProducao status,
            Instant inicio,
            Instant fim);

    @Query("""
            SELECT o
            FROM OrdemProducao o
            WHERE o.status <> :statusConcluida
               OR (
                    o.status = :statusConcluida
                    AND o.concluidaEm >= :inicio
                    AND o.concluidaEm < :fim
               )
            ORDER BY o.horarioEntrega ASC
            """)
    List<OrdemProducao> findOrdensKanbanPadrao(
            @Param("statusConcluida") StatusOrdemProducao statusConcluida,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim);

}
