package br.com.smartprod.receita;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Ficha técnica (receita) com ingredientes e modo de preparo passo a passo (RF005). Nunca sai direto na API: use os DTOs. */
@Entity
@Table(name = "fichas_tecnicas")
public class FichaTecnica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Quantas unidades a receita rende na quantidade base (ex.: 50 pães). */
    @Column(name = "rendimento_padrao", nullable = false)
    private int rendimentoPadrao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "fichaTecnica", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<FichaIngrediente> ingredientes = new ArrayList<>();

    @OneToMany(mappedBy = "fichaTecnica", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    private List<PassoPreparo> passos = new ArrayList<>();

    protected FichaTecnica() {
    }

    public FichaTecnica(String nome, int rendimentoPadrao) {
        this.nome = nome;
        this.rendimentoPadrao = rendimentoPadrao;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getRendimentoPadrao() {
        return rendimentoPadrao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public List<FichaIngrediente> getIngredientes() {
        return Collections.unmodifiableList(ingredientes);
    }

    public List<PassoPreparo> getPassos() {
        return Collections.unmodifiableList(passos);
    }

    public void adicionarIngrediente(FichaIngrediente ingrediente) {
        ingredientes.add(ingrediente);
    }

    public void adicionarPasso(PassoPreparo passo) {
        passos.add(passo);
    }
}