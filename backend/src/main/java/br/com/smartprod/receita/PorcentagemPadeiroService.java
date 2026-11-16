package br.com.smartprod.receita;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Porcentagem do Padeiro (RF006): a farinha vale 100% e cada ingrediente é medido em relação a ela.
 * Havendo mais de uma farinha, juntas somam 100%. O resultado tem 2 casas decimais (arredondamento
 * HALF_UP) e vem na mesma ordem dos ingredientes recebidos.
 *
 * <p>Atenção: usa a quantidade base como está, sem converter unidades (g, kg, ml, L). A conversão
 * depende da questão Q7 do docs/08.
 */
@Service
public class PorcentagemPadeiroService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final int CASAS_DECIMAIS = 2;

    public List<BigDecimal> calcular(List<FichaIngrediente> ingredientes) {
        if (ingredientes.isEmpty()) {
            return List.of();
        }
        BigDecimal totalFarinha = ingredientes.stream()
                .filter(FichaIngrediente::isEhFarinha)
                .map(FichaIngrediente::getQuantidadeBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalFarinha.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A ficha técnica precisa ter ao menos um ingrediente marcado como farinha.");
        }
        return ingredientes.stream()
                .map(ingrediente -> ingrediente.getQuantidadeBase().multiply(CEM)
                        .divide(totalFarinha, CASAS_DECIMAIS, RoundingMode.HALF_UP))
                .toList();
    }
}