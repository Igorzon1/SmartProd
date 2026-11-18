package br.com.smartprod.receita;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.receita.dto.CalculoPorEstoqueRequest;
import br.com.smartprod.receita.dto.CalculoPorEstoqueResponse;
import br.com.smartprod.receita.dto.CalculoResponse;

/**
 * Calculadora da ficha técnica (RF007): recalcula os ingredientes pela quantidade de unidades desejada
 * ou descobre quantas unidades o estoque informado permite produzir.
 *
 * <p>Regras: um insumo que não aparece no estoque conta como estoque zero; as unidades possíveis são
 * sempre inteiras (arredondadas para baixo); as quantidades têm 4 casas decimais. Como na Porcentagem
 * do Padeiro, as quantidades são usadas como estão, sem converter unidades (questão Q7 do docs/08).
 */
@Service
public class CalculoFichaService {

    private static final int CASAS_DECIMAIS = 4;
    private static final BigDecimal TETO_UNIDADES = BigDecimal.valueOf(Long.MAX_VALUE);

    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final PorcentagemPadeiroService porcentagemPadeiroService;

    public CalculoFichaService(FichaTecnicaRepository fichaTecnicaRepository,
            PorcentagemPadeiroService porcentagemPadeiroService) {
        this.fichaTecnicaRepository = fichaTecnicaRepository;
        this.porcentagemPadeiroService = porcentagemPadeiroService;
    }

    /** Recalcula a ficha para {@code unidades}: cada quantidade vira base x unidades / rendimento padrão. */
    @Transactional(readOnly = true)
    public CalculoResponse calcular(Long fichaId, int unidades) {
        if (unidades <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A quantidade de unidades deve ser maior que zero.");
        }
        FichaTecnica ficha = buscarFicha(fichaId);
        List<FichaIngrediente> ingredientes = ficha.getIngredientes();
        List<BigDecimal> percentuais = porcentagemPadeiroService.calcular(ingredientes);
        BigDecimal rendimento = BigDecimal.valueOf(ficha.getRendimentoPadrao());
        BigDecimal fator = BigDecimal.valueOf(unidades).divide(rendimento, CASAS_DECIMAIS, RoundingMode.HALF_UP);
        return new CalculoResponse(fichaId, unidades, fator,
                recalcular(ingredientes, percentuais, rendimento, unidades));
    }

    /** Descobre quantas unidades o estoque permite e qual ingrediente acaba primeiro (o limitante). */
    @Transactional(readOnly = true)
    public CalculoPorEstoqueResponse calcularPorEstoque(Long fichaId, CalculoPorEstoqueRequest requisicao) {
        FichaTecnica ficha = buscarFicha(fichaId);
        List<FichaIngrediente> ingredientes = ficha.getIngredientes();
        if (ingredientes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A ficha técnica não tem ingredientes.");
        }
        List<BigDecimal> percentuais = porcentagemPadeiroService.calcular(ingredientes);

        Map<Long, BigDecimal> estoque = new HashMap<>();
        for (CalculoPorEstoqueRequest.EstoqueItem item : requisicao.estoque()) {
            estoque.merge(item.insumoId(), item.quantidade(), BigDecimal::add);
        }

        BigDecimal rendimento = BigDecimal.valueOf(ficha.getRendimentoPadrao());
        long unidadesPossiveis = 0;
        FichaIngrediente limitante = null;
        for (FichaIngrediente ingrediente : ingredientes) {
            BigDecimal disponivel = estoque.getOrDefault(ingrediente.getInsumo().getId(), BigDecimal.ZERO);
            long possiveis = disponivel.multiply(rendimento)
                    .divide(ingrediente.getQuantidadeBase(), 0, RoundingMode.DOWN)
                    .min(TETO_UNIDADES)
                    .longValue();
            if (limitante == null || possiveis < unidadesPossiveis) {
                unidadesPossiveis = possiveis;
                limitante = ingrediente;
            }
        }

        return new CalculoPorEstoqueResponse(
                unidadesPossiveis,
                new CalculoPorEstoqueResponse.IngredienteLimitante(
                        limitante.getInsumo().getId(), limitante.getInsumo().getNome()),
                recalcular(ingredientes, percentuais, rendimento, unidadesPossiveis));
    }

    private List<CalculoResponse.IngredienteCalculado> recalcular(List<FichaIngrediente> ingredientes,
            List<BigDecimal> percentuais, BigDecimal rendimento, long unidades) {
        BigDecimal quantidadeUnidades = BigDecimal.valueOf(unidades);
        return IntStream.range(0, ingredientes.size())
                .mapToObj(i -> {
                    FichaIngrediente ingrediente = ingredientes.get(i);
                    BigDecimal quantidade = ingrediente.getQuantidadeBase()
                            .multiply(quantidadeUnidades)
                            .divide(rendimento, CASAS_DECIMAIS, RoundingMode.HALF_UP);
                    return new CalculoResponse.IngredienteCalculado(
                            ingrediente.getInsumo().getId(),
                            ingrediente.getInsumo().getNome(),
                            ingrediente.getInsumo().getUnidadeMedida(),
                            quantidade,
                            percentuais.get(i));
                })
                .toList();
    }

    private FichaTecnica buscarFicha(Long id) {
        return fichaTecnicaRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Ficha técnica não encontrada."));
    }
}