package br.com.smartprod.receita;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Passo do modo de preparo de uma ficha técnica (RF005). */
@Entity
@Table(name = "passos_preparo")
public class PassoPreparo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ficha_tecnica_id", nullable = false)
    private FichaTecnica fichaTecnica;

    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false, length = 120)
    private String etapa;

    @Column(nullable = false, length = 1000)
    private String descricao;

    /** Duração do passo em segundos. Opcional. */
    @Column(name = "duracao_segundos")
    private Integer duracaoSegundos;

    protected PassoPreparo() {
    }

    public PassoPreparo(FichaTecnica fichaTecnica, int ordem, String etapa, String descricao, Integer duracaoSegundos) {
        this.fichaTecnica = fichaTecnica;
        this.ordem = ordem;
        this.etapa = etapa;
        this.descricao = descricao;
        this.duracaoSegundos = duracaoSegundos;
    }

    public Long getId() {
        return id;
    }

    public FichaTecnica getFichaTecnica() {
        return fichaTecnica;
    }

    public int getOrdem() {
        return ordem;
    }

    public String getEtapa() {
        return etapa;
    }

    public String getDescricao() {
        return descricao;
    }

    public Integer getDuracaoSegundos() {
        return duracaoSegundos;
    }
}