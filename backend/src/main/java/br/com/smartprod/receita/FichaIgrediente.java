package br.com.smartprod.receita;

import java.math.BigDecimal;

import br.com.smartprod.insumo.Insumo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Ingrediente de uma ficha técnica: qual insumo e a quantidade base (RF005). */
@Entity
@Table(name = "ficha_ingredientes")
public class FichaIngrediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ficha_tecnica_id", nullable = false)
    private FichaTecnica fichaTecnica;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    /** Quantidade na unidade de medida do insumo, para o rendimento padrão da ficha. */
    @Column(name = "quantidade_base", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantidadeBase;

    /** Marca a farinha: ela é a base de 100% da Porcentagem do Padeiro (RF006). */
    @Column(name = "eh_farinha", nullable = false)
    private boolean ehFarinha;

    protected FichaIngrediente() {
    }

    public FichaIngrediente(FichaTecnica fichaTecnica, Insumo insumo, BigDecimal quantidadeBase, boolean ehFarinha) {
        this.fichaTecnica = fichaTecnica;
        this.insumo = insumo;
        this.quantidadeBase = quantidadeBase;
        this.ehFarinha = ehFarinha;
    }

    public Long getId() {
        return id;
    }

    public FichaTecnica getFichaTecnica() {
        return fichaTecnica;
    }

    public Insumo getInsumo() {
        return insumo;
    }

    public BigDecimal getQuantidadeBase() {
        return quantidadeBase;
    }

    public boolean isEhFarinha() {
        return ehFarinha;
    }
}