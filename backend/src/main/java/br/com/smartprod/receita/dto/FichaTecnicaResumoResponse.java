package br.com.smartprod.receita.dto;

import br.com.smartprod.receita.FichaTecnica;

/** Item da lista de fichas técnicas (RF005). Sem custos: serve a Gerente e Funcionário. */
public record FichaTecnicaResumoResponse(Long id, String nome, int rendimentoPadrao) {

    public static FichaTecnicaResumoResponse de(FichaTecnica ficha) {
        return new FichaTecnicaResumoResponse(ficha.getId(), ficha.getNome(), ficha.getRendimentoPadrao());
    }
}