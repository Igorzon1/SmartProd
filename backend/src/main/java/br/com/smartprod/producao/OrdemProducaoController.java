
package br.com.smartprod.producao;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.smartprod.producao.dto.OrdemProducaoConclusaoRequest;
import br.com.smartprod.producao.dto.OrdemProducaoCriacaoRequest;
import br.com.smartprod.producao.dto.OrdemProducaoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ordens-producao")
@Tag(
        name = "Ordens de Produção",
        description = "Abertura, consulta e gerenciamento de ordens de produção (RF008 e RF009)")
public class OrdemProducaoController {

    private final OrdemProducaoService service;

    public OrdemProducaoController(OrdemProducaoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(
            summary = "Lista as ordens de produção",
            description = "Consulta as ordens de produção, permitindo filtrar por status e data de entrega.")
    @ApiResponse(responseCode = "200", description = "Ordens consultadas com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão")
    public ResponseEntity<List<OrdemProducaoResponse>> listar(
            @RequestParam(required = false) StatusOrdemProducao status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {

        List<OrdemProducao> ordens = service.listarPorFiltros(status, data);

        List<OrdemProducaoResponse> resposta = ordens.stream()
                .map(OrdemProducaoResponse::de)
                .toList();

        return ResponseEntity.ok(resposta);
    }

    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(
            summary = "Abre uma nova ordem de produção",
            description = "Permite ao Gerente criar uma ordem vinculada a uma ficha técnica, "
                    + "informando quantidade, horário de entrega e funcionário responsável.")
    @ApiResponse(responseCode = "201", description = "Ordem criada com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos para criação da ordem")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Apenas gerentes podem criar ordens")
    @ApiResponse(responseCode = "404", description = "Ficha técnica ou responsável não encontrado")
    public ResponseEntity<OrdemProducaoResponse> criar(
            @Valid @RequestBody OrdemProducaoCriacaoRequest request,
            Authentication authentication) {

        Long gerenteId = Long.valueOf(authentication.getName());

        OrdemProducao ordem = service.criar(request, gerenteId);

        URI location = URI.create("/api/ordens-producao/" + ordem.getId());

        return ResponseEntity
                .created(location)
                .body(OrdemProducaoResponse.de(ordem));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(
            summary = "Consulta uma ordem de produção",
            description = "Retorna os detalhes de uma ordem, incluindo produto, "
                    + "quantidade, status e responsáveis.")
    @ApiResponse(responseCode = "200", description = "Ordem encontrada")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão")
    @ApiResponse(responseCode = "404", description = "Ordem não encontrada")
    public ResponseEntity<OrdemProducaoResponse> buscarPorId(
            @PathVariable Long id) {

        OrdemProducaoResponse resposta = OrdemProducaoResponse.de(
                service.buscarPorId(id));

        return ResponseEntity.ok(resposta);
    }

    @PatchMapping("/{id}/iniciar")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(
            summary = "Inicia uma ordem de produção",
            description = "Altera o status da ordem de PENDENTE para EM_ANDAMENTO. "
                    + "Transições inválidas são rejeitadas.")
    @ApiResponse(responseCode = "200", description = "Ordem iniciada com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão")
    @ApiResponse(responseCode = "404", description = "Ordem não encontrada")
    @ApiResponse(responseCode = "409", description = "A ordem não está pendente")
    public ResponseEntity<OrdemProducaoResponse> iniciar(
            @PathVariable Long id) {

        OrdemProducaoResponse resposta = OrdemProducaoResponse.de(
                service.iniciar(id));

        return ResponseEntity.ok(resposta);
    }

    @PatchMapping("/{id}/concluir")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(
            summary = "Conclui uma ordem de produção",
            description = "Altera o status de EM_ANDAMENTO para CONCLUIDA, "
                    + "registrando a quantidade efetivamente produzida.")
    @ApiResponse(responseCode = "200", description = "Ordem concluída com sucesso")
    @ApiResponse(responseCode = "400", description = "Quantidade produzida inválida")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão")
    @ApiResponse(responseCode = "404", description = "Ordem não encontrada")
    @ApiResponse(responseCode = "409", description = "A ordem não está em andamento")
    public ResponseEntity<OrdemProducaoResponse> concluir(
            @PathVariable Long id,
            @Valid @RequestBody OrdemProducaoConclusaoRequest request) {

        OrdemProducaoResponse resposta = OrdemProducaoResponse.de(
                service.concluir(id, request.quantidadeProduzida()));

        return ResponseEntity.ok(resposta);
    }
}
