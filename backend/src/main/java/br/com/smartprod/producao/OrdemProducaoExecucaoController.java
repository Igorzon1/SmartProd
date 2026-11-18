
package br.com.smartprod.producao;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.smartprod.producao.dto.OrdemProducaoExecucaoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ordens-producao")
@Tag(
        name = "Execução de Produção",
        description = "Ingredientes ajustados ao lote e etapas de preparo das ordens (RF010)")
public class OrdemProducaoExecucaoController {

    private final OrdemProducaoExecucaoService service;

    public OrdemProducaoExecucaoController(
            OrdemProducaoExecucaoService service) {
        this.service = service;
    }

    @GetMapping("/{id}/execucao")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(
            summary = "Consulta os dados de execução de uma ordem",
            description = "Retorna os ingredientes com quantidades ajustadas ao lote, "
                    + "as etapas de preparo e suas durações em segundos, sem expor custos.")
    @ApiResponse(responseCode = "200", description = "Dados de execução consultados com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão")
    @ApiResponse(responseCode = "404", description = "Ordem não encontrada")
    @ApiResponse(responseCode = "409", description = "Ficha técnica ausente ou com rendimento inválido")
    public ResponseEntity<OrdemProducaoExecucaoResponse> buscarExecucao(
            @PathVariable Long id) {

        return ResponseEntity.ok(service.buscarExecucao(id));
    }
}
