package br.com.smartprod.receita;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.smartprod.receita.dto.FichaTecnicaRequest;
import br.com.smartprod.receita.dto.FichaTecnicaResponse;
import br.com.smartprod.receita.dto.FichaTecnicaResumoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Fichas técnicas (RF005). Leitura de qualquer usuário logado, sem custos (RNF-S002); cadastro e edição
 * só do Gerente.
 */
@RestController
@RequestMapping("/api/fichas-tecnicas")
@Tag(name = "Fichas técnicas", description = "Receitas com ingredientes e modo de preparo (RF005)")
public class FichaTecnicaController {

    private final FichaTecnicaService fichaTecnicaService;

    public FichaTecnicaController(FichaTecnicaService fichaTecnicaService) {
        this.fichaTecnicaService = fichaTecnicaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(summary = "Lista as fichas técnicas por nome")
    public List<FichaTecnicaResumoResponse> listar() {
        return fichaTecnicaService.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(summary = "Consulta uma ficha técnica com ingredientes e passos")
    public FichaTecnicaResponse buscar(@PathVariable Long id) {
        return fichaTecnicaService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Cadastra uma ficha técnica com ingredientes e passos (só Gerente)")
    public ResponseEntity<FichaTecnicaResponse> criar(@Valid @RequestBody FichaTecnicaRequest requisicao) {
        FichaTecnicaResponse criada = fichaTecnicaService.criar(requisicao);
        return ResponseEntity.created(URI.create("/api/fichas-tecnicas/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Edita nome, rendimento, ingredientes e passos da ficha (só Gerente)")
    public FichaTecnicaResponse atualizar(@PathVariable Long id, @Valid @RequestBody FichaTecnicaRequest requisicao) {
        return fichaTecnicaService.atualizar(id, requisicao);
    }
}