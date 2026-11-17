
package br.com.smartprod.perda;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.smartprod.perda.dto.RegistroPerdaCriacaoRequest;
import br.com.smartprod.perda.dto.RegistroPerdaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ordens-producao/{ordemId}/perdas")
@Tag(
        name = "Perdas de Produção",
        description = "Registro e consulta de perdas e refugos durante a produção (RF011)")
public class RegistroPerdaController {

    private final RegistroPerdaService service;

    public RegistroPerdaController(RegistroPerdaService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(
            summary = "Registra uma perda durante a produção",
            description = "Registra etapa, quantidade, unidade e motivo da perda. "
                    + "A ordem deve estar em andamento. "
                    + "Gerentes e funcionários podem realizar o registro.")
    @ApiResponse(responseCode = "201", description = "Perda registrada com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos para o registro da perda")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão")
    @ApiResponse(responseCode = "404", description = "Ordem ou usuário não encontrado")
    @ApiResponse(responseCode = "409", description = "A ordem não está em andamento")
    public ResponseEntity<RegistroPerdaResponse> registrar(
            @PathVariable Long ordemId,
            @Valid @RequestBody RegistroPerdaCriacaoRequest request,
            Authentication authentication) {

        Long usuarioId = Long.valueOf(authentication.getName());

        RegistroPerda perda = service.registrar(
                ordemId,
                request.etapa(),
                request.quantidade(),
                request.unidade(),
                request.motivo(),
                usuarioId);

        RegistroPerdaResponse resposta = RegistroPerdaResponse.de(perda);

        return ResponseEntity
                .created(URI.create(
                        "/api/ordens-producao/" + ordemId
                                + "/perdas/" + perda.getId()))
                .body(resposta);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(
            summary = "Lista as perdas de uma ordem de produção",
            description = "Consulta os registros de perdas e refugos associados "
                    + "a uma ordem de produção, sem apresentar valores financeiros.")
    @ApiResponse(responseCode = "200", description = "Perdas consultadas com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão")
    @ApiResponse(responseCode = "404", description = "Ordem não encontrada")
    public ResponseEntity<List<RegistroPerdaResponse>> listar(
            @PathVariable Long ordemId) {

        List<RegistroPerdaResponse> resposta = service.listarPorOrdem(ordemId)
                .stream()
                .map(RegistroPerdaResponse::de)
                .toList();

        return ResponseEntity.ok(resposta);
    }
}
