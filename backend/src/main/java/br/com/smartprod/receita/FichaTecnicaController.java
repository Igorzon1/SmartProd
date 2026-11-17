package br.com.smartprod.receita;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.smartprod.receita.dto.FichaTecnicaResponse;
import br.com.smartprod.receita.dto.FichaTecnicaResumoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Consulta de fichas técnicas (RF005). Leitura de qualquer usuário logado, sem custos (RNF-S002). */
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
}