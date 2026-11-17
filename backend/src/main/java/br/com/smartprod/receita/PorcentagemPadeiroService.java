package br.com.smartprod.receita;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Porcentagem do Padeiro (RF006): a farinha vale 100% e cada ingrediente é medido em relação a ela.
 * Havendo mais de uma farinha, juntas somam 100%. O resultado tem 2 casas decimais (arredondamento
 * HALF_UP) e vem na mesma ordem dos ingredientes recebidos.
 *
 * <p>Unidades (docs/08, Q7): kg vira g e L vira ml antes da conta, e 1 ml conta como 1 g (vale para água
 * e leite, é aproximado para óleo e mel). Ingrediente em {@code UN} (ovo, por exemplo) não tem como ser
 * comparado com a farinha e fica sem percentual.
 *
 * <p>O percentual é {@code null} quando não dá para calculá-lo: ficha sem farinha, ou ingrediente em
 * {@code UN}. Isso não é erro, porque a ficha continua válida para consulta e execução (um brigadeiro não
 * leva farinha). Quem precisa obrigatoriamente da farinha, como a calculadora (RF007), valida por conta própria.
 */
@Service
public class PorcentagemPadeiroService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal MIL = BigDecimal.valueOf(1000);
    private static final int CASAS_DECIMAIS = 2;

    public List<BigDecimal> calcular(List<FichaIngrediente> ingredientes) {
        BigDecimal totalFarinha = ingredientes.stream()
                .filter(FichaIngrediente::isEhFarinha)
                .map(PorcentagemPadeiroService::emUnidadeBase)
                .filter(quantidade -> quantidade != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean temFarinha = totalFarinha.signum() > 0;

        List<BigDecimal> percentuais = new ArrayList<>(ingredientes.size());
        for (FichaIngrediente ingrediente : ingredientes) {
            BigDecimal quantidade = emUnidadeBase(ingrediente);
            percentuais.add(temFarinha && quantidade != null
                    ? quantidade.multiply(CEM).divide(totalFarinha, CASAS_DECIMAIS, RoundingMode.HALF_UP)
                    : null);
        }
        return percentuais;
    }

    /** Quantidade em g (massa) ou ml (volume); {@code null} para {@code UN}, que não se converte. */
    private static BigDecimal emUnidadeBase(FichaIngrediente ingrediente) {
        BigDecimal quantidade = ingrediente.getQuantidadeBase();
        return switch (ingrediente.getInsumo().getUnidadeMedida()) {
            case G, ML -> quantidade;
            case KG, L -> quantidade.multiply(MIL);
            case UN -> null;
        };
    }
}
