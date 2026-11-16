package br.com.smartprod.receita.dto;

import java.math.BigDecimal;
import java.util.List;

import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.receita.FichaIngrediente;
import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.PassoPreparo;

/** Ficha técnica completa (RF005). Sem custos: serve a Gerente e Funcionário (RNF-S002). */
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
            boolean ehFarinha) {

        static IngredienteResponse de(FichaIngrediente ingrediente) {
            return new IngredienteResponse(
                    ingrediente.getInsumo().getId(),
                    ingrediente.getInsumo().getNome(),
                    ingrediente.getInsumo().getUnidadeMedida(),
                    ingrediente.getQuantidadeBase(),
                    ingrediente.isEhFarinha());
        }
    }

    public record PassoResponse(int ordem, String etapa, String descricao, Integer duracaoSegundos) {

        static PassoResponse de(PassoPreparo passo) {
            return new PassoResponse(passo.getOrdem(), passo.getEtapa(), passo.getDescricao(),
                    passo.getDuracaoSegundos());
        }
    }

    public static FichaTecnicaResponse de(FichaTecnica ficha) {
        return new FichaTecnicaResponse(
                ficha.getId(),
                ficha.getNome(),
                ficha.getRendimentoPadrao(),
                ficha.getIngredientes().stream().map(IngredienteResponse::de).toList(),
                ficha.getPassos().stream().map(PassoResponse::de).toList());
    }
}