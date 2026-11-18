package br.com.smartprod.receita;

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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Calculadora de receitas (RF006, RF007). Gerente e Funcionário; nunca mostra custos (RNF-S002). */
@RestController
@RequestMapping("/api/fichas-tecnicas")
@Tag(name = "Calculadora", description = "Recálculo de receitas por demanda e por estoque (RF007)")
public class CalculadoraController {

    private final CalculadoraService calculadoraService;

    public CalculadoraController(CalculadoraService calculadoraService) {
        this.calculadoraService = calculadoraService;
    }

    @GetMapping("/{id}/calculo")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(summary = "Quanto de cada ingrediente usar para produzir N unidades")
    public CalculoResponse porDemanda(@PathVariable Long id, @RequestParam int unidades) {
        return calculadoraService.porDemanda(id, unidades);
    }

    @PostMapping("/{id}/calculo-por-estoque")
    @PreAuthorize("hasAnyRole('GERENTE', 'FUNCIONARIO')")
    @Operation(summary = "Quantas unidades dá para produzir com o estoque informado, e o ingrediente que limita")
    public CalculoPorEstoqueResponse porEstoque(@PathVariable Long id,
            @Valid @RequestBody CalculoPorEstoqueRequest requisicao) {
        return calculadoraService.porEstoque(id, requisicao.estoque());
    }
}
