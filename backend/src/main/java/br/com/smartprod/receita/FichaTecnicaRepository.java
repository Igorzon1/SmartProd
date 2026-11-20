package br.com.smartprod.receita;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FichaTecnicaRepository extends JpaRepository<FichaTecnica, Long> {

    List<FichaTecnica> findAllByOrderByNomeAsc();

    /**
     * Ingredientes de todas as fichas, numa consulta só, para o custo das receitas: cada linha é
     * {@code [fichaId, quantidadeBase, unidadeMedida, custoUnitario]}.
     */
    @Query("""
            SELECT i.fichaTecnica.id, i.quantidadeBase, ins.unidadeMedida, ins.custoUnitario
            FROM FichaIngrediente i JOIN i.insumo ins
            """)
    List<Object[]> ingredientesComCusto();
}
