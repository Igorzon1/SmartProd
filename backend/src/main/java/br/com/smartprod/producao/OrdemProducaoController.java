
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
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ordens-producao")
public class OrdemProducaoController {

    private final OrdemProducaoService service;

    public OrdemProducaoController(OrdemProducaoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    public ResponseEntity<List<OrdemProducaoResponse>> listar(
            @RequestParam(required = false) StatusOrdemProducao status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        List<OrdemProducao> ordens = service.listarPorFiltros(status, data);

        List<OrdemProducaoResponse> resposta = ordens.stream()
                .map(OrdemProducaoResponse::de)
                .toList();

        return ResponseEntity.ok(resposta);
    }

    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
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
    public ResponseEntity<OrdemProducaoResponse> buscarPorId(
            @PathVariable Long id) {

        OrdemProducaoResponse resposta = OrdemProducaoResponse.de(
                service.buscarPorId(id));

        return ResponseEntity.ok(resposta);
    }

    @PatchMapping("/{id}/iniciar")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    public ResponseEntity<OrdemProducaoResponse> iniciar(
            @PathVariable Long id) {

        OrdemProducaoResponse resposta = OrdemProducaoResponse.de(
                service.iniciar(id));

        return ResponseEntity.ok(resposta);
    }

    @PatchMapping("/{id}/concluir")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    public ResponseEntity<OrdemProducaoResponse> concluir(
            @PathVariable Long id,
            @Valid @RequestBody OrdemProducaoConclusaoRequest request) {

        OrdemProducaoResponse resposta = OrdemProducaoResponse.de(
                service.concluir(id, request.quantidadeProduzida()));

        return ResponseEntity.ok(resposta);
    }
}
