package br.com.smartprod.receita;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.smartprod.receita.dto.CalculoPorEstoqueRequest;
import br.com.smartprod.receita.dto.CalculoPorEstoqueResponse;
import br.com.smartprod.receita.dto.CalculoResponse;
import br.com.smartprod.receita.dto.FichaTecnicaResponse;
import br.com.smartprod.receita.dto.FichaTecnicaResumoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Consulta e cálculo de fichas técnicas (RF005, RF006, RF007). Leitura e cálculo de qualquer usuário
 * logado, sem custos (RNF-S002).
 */
@RestController
@RequestMapping("/api/fichas-tecnicas")
@Tag(name = "Fichas técnicas", description = "Receitas com ingredientes, modo de preparo e calculadora (RF005 a RF007)")
public class FichaTecnicaController {

    private final FichaTecnicaService fichaTecnicaService;
    private final CalculoFichaService calculoFichaService;

    public FichaTecnicaController(FichaTecnicaService fichaTecnicaService, CalculoFichaService calculoFichaService) {
        this.fichaTecnicaService = fichaTecnicaService;
        this.calculoFichaService = calculoFichaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(summary = "Lista as fichas técnicas por nome")
    public List<FichaTecnicaResumoResponse> listar() {
        return fichaTecnicaService.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(summary = "Consulta uma ficha técnica com ingredientes, passos e Porcentagem do Padeiro")
    public FichaTecnicaResponse buscar(@PathVariable Long id) {
        return fichaTecnicaService.buscar(id);
    }

    @GetMapping("/{id}/calculo")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(summary = "Recalcula os ingredientes para a quantidade de unidades desejada")
    public CalculoResponse calcular(@PathVariable Long id, @RequestParam int unidades) {
        return calculoFichaService.calcular(id, unidades);
    }

    @PostMapping("/{id}/calculo-por-estoque")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(summary = "Informa o estoque por insumo e devolve as unidades possíveis e o ingrediente limitante")
    public CalculoPorEstoqueResponse calcularPorEstoque(@PathVariable Long id,
            @Valid @RequestBody CalculoPorEstoqueRequest requisicao) {
        return calculoFichaService.calcularPorEstoque(id, requisicao);
    }
}