package br.com.smartprod.dashboard.dto;

/** Status consolidado das OPs (RF015). Concluídas conta só as concluídas hoje, como o Kanban. */
public record StatusOpsResponse(long pendentes, long emAndamento, long concluidas) {
}
