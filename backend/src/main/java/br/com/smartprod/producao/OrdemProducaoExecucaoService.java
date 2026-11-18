
package br.com.smartprod.producao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.producao.dto.ExecucaoEtapaResponse;
import br.com.smartprod.producao.dto.ExecucaoIngredienteResponse;
import br.com.smartprod.producao.dto.OrdemProducaoExecucaoResponse;
import br.com.smartprod.receita.FichaIngrediente;
import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.PassoPreparo;

@Service
public class OrdemProducaoExecucaoService {

    private final OrdemProducaoService ordemProducaoService;

    public OrdemProducaoExecucaoService(
            OrdemProducaoService ordemProducaoService) {
        this.ordemProducaoService = ordemProducaoService;
    }

    @Transactional(readOnly = true)
    public OrdemProducaoExecucaoResponse buscarExecucao(Long ordemId) {
        OrdemProducao ordem = ordemProducaoService.buscarPorId(ordemId);

        FichaTecnica ficha = ordem.getFichaTecnica();

        if (ficha == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A ordem de produção não possui ficha técnica associada.");
        }

        if (ficha.getRendimentoPadrao() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O rendimento padrão da ficha técnica deve ser maior que zero.");
        }

        List<ExecucaoIngredienteResponse> ingredientes = ficha.getIngredientes()
                .stream()
                .map(ingrediente -> converterIngrediente(
                        ingrediente,
                        ordem.getQuantidadeUnidades(),
                        ficha.getRendimentoPadrao()))
                .toList();

        List<ExecucaoEtapaResponse> etapas = ficha.getPassos()
                .stream()
                .map(this::converterEtapa)
                .sorted((primeira, segunda) -> Integer.compare(primeira.ordem(), segunda.ordem()))
                .toList();

        return new OrdemProducaoExecucaoResponse(
                ordem.getId(),
                ficha.getId(),
                ficha.getNome(),
                ordem.getQuantidadeUnidades(),
                ordem.getStatus(),
                ingredientes,
                etapas);
    }

    private ExecucaoIngredienteResponse converterIngrediente(
            FichaIngrediente ingrediente,
            Integer quantidadeUnidades,
            Integer rendimentoPadrao) {

        BigDecimal quantidadeAjustada = ingrediente.getQuantidadeBase()
                .multiply(BigDecimal.valueOf(quantidadeUnidades))
                .divide(
                        BigDecimal.valueOf(rendimentoPadrao),
                        3,
                        RoundingMode.HALF_UP);

        return new ExecucaoIngredienteResponse(
                ingrediente.getInsumo().getId(),
                ingrediente.getInsumo().getNome(),
                quantidadeAjustada,
                ingrediente.getInsumo().getUnidadeMedida());
    }

    private ExecucaoEtapaResponse converterEtapa(PassoPreparo passo) {
        return new ExecucaoEtapaResponse(
                passo.getOrdem(),
                passo.getEtapa(),
                passo.getDescricao(),
                passo.getDuracaoSegundos());
    }
}
