
package br.com.smartprod.producao.dto;

import java.time.Instant;

import br.com.smartprod.producao.OrdemProducao;
import br.com.smartprod.producao.StatusOrdemProducao;
import br.com.smartprod.receita.FichaTecnica;

public record OrdemProducaoResponse(
        Long id,
        Long fichaTecnicaId,
        String nomeProduto,
        Integer quantidadeUnidades,
        Instant horarioEntrega,
        StatusOrdemProducao status,
        Instant iniciadaEm,
        Instant concluidaEm,
        Integer quantidadeProduzida,
        ResponsavelResponse responsavel,
        CriadoPorResponse criadoPor) {

    public static OrdemProducaoResponse de(OrdemProducao ordem) {
        FichaTecnica ficha = ordem.getFichaTecnica();

        return new OrdemProducaoResponse(
                ordem.getId(),
                ficha != null ? ficha.getId() : null,
                ficha != null ? ficha.getNome() : null,
                ordem.getQuantidadeUnidades(),
                ordem.getHorarioEntrega(),
                ordem.getStatus(),
                ordem.getIniciadaEm(),
                ordem.getConcluidaEm(),
                ordem.getQuantidadeProduzida(),
                new ResponsavelResponse(
                        ordem.getResponsavel().getId(),
                        ordem.getResponsavel().getNome()),
                new CriadoPorResponse(
                        ordem.getCriadoPor().getId(),
                        ordem.getCriadoPor().getNome()));
    }

    public record ResponsavelResponse(
            Long id,
            String nome) {
    }

    public record CriadoPorResponse(
            Long id,
            String nome) {
    }
}
