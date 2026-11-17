package br.com.smartprod.receita.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.receita.FichaIngrediente;
import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.PassoPreparo;

/** Ficha técnica completa (RF005, RF006). Sem custos: serve a Gerente e Funcionário (RNF-S002). */
public record FichaTecnicaResponse(
        Long id,
        String nome,
        int rendimentoPadrao,
        List<IngredienteResponse> ingredientes,
        List<PassoResponse> passos) {

    public record IngredienteResponse(
            Long insumoId,
            String insumoNome,
            UnidadeMedida unidadeMedida,
            BigDecimal quantidadeBase,
            boolean ehFarinha,
            BigDecimal percentualPadeiro) {

        static IngredienteResponse de(FichaIngrediente ingrediente, BigDecimal percentualPadeiro) {
            return new IngredienteResponse(
                    ingrediente.getInsumo().getId(),
                    ingrediente.getInsumo().getNome(),
                    ingrediente.getInsumo().getUnidadeMedida(),
                    ingrediente.getQuantidadeBase(),
                    ingrediente.isEhFarinha(),
                    percentualPadeiro);
        }
    }

    public record PassoResponse(int ordem, String etapa, String descricao, Integer duracaoSegundos) {

        static PassoResponse de(PassoPreparo passo) {
            return new PassoResponse(passo.getOrdem(), passo.getEtapa(), passo.getDescricao(),
                    passo.getDuracaoSegundos());
        }
    }

    /** @param percentuais um valor por ingrediente, na mesma ordem de {@code ficha.getIngredientes()} */
    public static FichaTecnicaResponse de(FichaTecnica ficha, List<BigDecimal> percentuais) {
        List<FichaIngrediente> ingredientes = ficha.getIngredientes();
        return new FichaTecnicaResponse(
                ficha.getId(),
                ficha.getNome(),
                ficha.getRendimentoPadrao(),
                IntStream.range(0, ingredientes.size())
                        .mapToObj(i -> IngredienteResponse.de(ingredientes.get(i), percentuais.get(i)))
                        .toList(),
                ficha.getPassos().stream().map(PassoResponse::de).toList());
    }
}