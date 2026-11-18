package br.com.smartprod.receita;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.receita.dto.CalculoPorEstoqueRequest.EstoqueItem;
import br.com.smartprod.receita.dto.CalculoPorEstoqueResponse;
import br.com.smartprod.receita.dto.CalculoPorEstoqueResponse.IngredienteLimitante;
import br.com.smartprod.receita.dto.CalculoResponse;
import br.com.smartprod.receita.dto.IngredienteCalculadoResponse;

/**
 * Calculadora de receitas (RF007), com a Porcentagem do Padeiro de cada ingrediente (RF006). As quantidades
 * ficam na unidade de medida do insumo e saem com 3 casas decimais (arredondamento HALF_UP).
 *
 * <pre>
 * por demanda:  quantidade_i = quantidadeBase_i × unidades / rendimentoPadrao
 * por estoque:  unidades_i   = piso(estoque_i × rendimentoPadrao / quantidadeBase_i)
 *               unidades     = menor unidades_i  (o ingrediente dele é o limitante)
 * </pre>
 */
@Service
public class CalculadoraService {

    private static final int CASAS = 3;

    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final PorcentagemPadeiroService porcentagemPadeiroService;

    public CalculadoraService(FichaTecnicaRepository fichaTecnicaRepository,
            PorcentagemPadeiroService porcentagemPadeiroService) {
        this.fichaTecnicaRepository = fichaTecnicaRepository;
        this.porcentagemPadeiroService = porcentagemPadeiroService;
    }

    /** Quanto de cada ingrediente usar para produzir {@code unidades}. */
    @Transactional(readOnly = true)
    public CalculoResponse porDemanda(Long fichaId, int unidades) {
        if (unidades < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe quantas unidades produzir (a partir de 1).");
        }
        FichaTecnica ficha = buscar(fichaId);
        BigDecimal fator = BigDecimal.valueOf(unidades)
                .divide(BigDecimal.valueOf(ficha.getRendimentoPadrao()), CASAS, RoundingMode.HALF_UP);
        return new CalculoResponse(ficha.getId(), unidades, fator, ingredientesPara(ficha, unidades));
    }

    /** Quantas unidades o estoque informado permite produzir, e qual ingrediente acaba primeiro. */
    @Transactional(readOnly = true)
    public CalculoPorEstoqueResponse porEstoque(Long fichaId, List<EstoqueItem> estoque) {
        Map<Long, BigDecimal> disponivel = new HashMap<>();
        for (EstoqueItem item : estoque) {
            if (disponivel.put(item.insumoId(), item.quantidade()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada insumo pode aparecer só uma vez no estoque.");
            }
        }
        FichaTecnica ficha = buscar(fichaId);
        if (ficha.getIngredientes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A ficha técnica não tem ingredientes para calcular.");
        }

        BigDecimal rendimento = BigDecimal.valueOf(ficha.getRendimentoPadrao());
        long menor = Long.MAX_VALUE;
        FichaIngrediente limitante = null;
        for (FichaIngrediente ingrediente : ficha.getIngredientes()) {
            long possiveis = disponivel.getOrDefault(ingrediente.getInsumo().getId(), BigDecimal.ZERO)
                    .multiply(rendimento)
                    .divide(ingrediente.getQuantidadeBase(), 0, RoundingMode.FLOOR)
                    .min(BigDecimal.valueOf(Integer.MAX_VALUE))
                    .longValue();
            if (possiveis < menor) {
                menor = possiveis;
                limitante = ingrediente;
            }
        }
        return new CalculoPorEstoqueResponse(ficha.getId(), menor,
                new IngredienteLimitante(limitante.getInsumo().getId(), limitante.getInsumo().getNome()),
                ingredientesPara(ficha, menor));
    }

    private FichaTecnica buscar(Long fichaId) {
        return fichaTecnicaRepository.findById(fichaId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Ficha técnica não encontrada."));
    }

    private List<IngredienteCalculadoResponse> ingredientesPara(FichaTecnica ficha, long unidades) {
        List<FichaIngrediente> ingredientes = ficha.getIngredientes();
        List<BigDecimal> percentuais = porcentagemPadeiroService.calcular(ingredientes);
        BigDecimal rendimento = BigDecimal.valueOf(ficha.getRendimentoPadrao());
        List<IngredienteCalculadoResponse> resultado = new ArrayList<>(ingredientes.size());
        for (int i = 0; i < ingredientes.size(); i++) {
            FichaIngrediente ingrediente = ingredientes.get(i);
            BigDecimal quantidade = ingrediente.getQuantidadeBase().multiply(BigDecimal.valueOf(unidades))
                    .divide(rendimento, CASAS, RoundingMode.HALF_UP);
            resultado.add(new IngredienteCalculadoResponse(ingrediente.getInsumo().getId(),
                    ingrediente.getInsumo().getNome(), ingrediente.getInsumo().getUnidadeMedida(), quantidade,
                    percentuais.get(i)));
        }
        return resultado;
    }
}
