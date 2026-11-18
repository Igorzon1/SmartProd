
package br.com.smartprod.producao.dto;

public record ExecucaoEtapaResponse(
        Integer ordem,
        String etapa,
        String descricao,
        Integer duracaoSegundos) {
}
