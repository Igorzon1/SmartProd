
package br.com.smartprod.perda;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.smartprod.producao.OrdemProducao;
import br.com.smartprod.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import br.com.smartprod.shared.DadosInvalidosException;

@Entity
@Table(name = "registros_perda")
public class RegistroPerda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_producao_id", nullable = false)
    private OrdemProducao ordemProducao;

    @Column(nullable = false, length = 120)
    private String etapa;

    @Column(name = "peso_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal pesoKg;

    @Column(name = "unidades")
    private Integer unidades;

    @Column(nullable = false, length = 500)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por_id", nullable = false)
    private Usuario registradoPor;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private Instant registradoEm;

    protected RegistroPerda() {
    }

    public RegistroPerda(
            OrdemProducao ordemProducao,
            String etapa,
            BigDecimal pesoKg,
            Integer unidades,
            String motivo,
            Usuario registradoPor) {

        if (ordemProducao == null) {
            throw new DadosInvalidosException(
                    "A ordem de produção é obrigatória.");
        }

        if (etapa == null || etapa.isBlank()) {
            throw new DadosInvalidosException(
                    "A etapa é obrigatória.");
        }

        if (pesoKg == null || pesoKg.signum() <= 0) {
            throw new DadosInvalidosException(
                    "O peso perdido deve ser maior que zero.");
        }

        if (pesoKg.scale() > 3) {
            throw new DadosInvalidosException(
                    "O peso perdido deve ter no máximo 3 casas decimais.");
        }

        if (pesoKg.precision() - pesoKg.scale() > 9) {
            throw new DadosInvalidosException(
                    "O peso perdido excede o limite permitido.");
        }

        if (unidades != null && unidades <= 0) {
            throw new DadosInvalidosException(
                    "A quantidade de unidades perdidas deve ser maior que zero.");
        }

        if (motivo == null || motivo.isBlank()) {
            throw new DadosInvalidosException(
                    "O motivo é obrigatório.");
        }

        if (registradoPor == null) {
            throw new DadosInvalidosException(
                    "O usuário que registrou a perda é obrigatório.");
        }

        this.ordemProducao = ordemProducao;
        this.etapa = etapa.trim();
        this.pesoKg = pesoKg;
        this.unidades = unidades;
        this.motivo = motivo.trim();
        this.registradoPor = registradoPor;
        this.registradoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public OrdemProducao getOrdemProducao() {
        return ordemProducao;
    }

    public String getEtapa() {
        return etapa;
    }

    public BigDecimal getPesoKg() {
        return pesoKg;
    }

    public Integer getUnidades() {
        return unidades;
    }

    public String getMotivo() {
        return motivo;
    }

    public Usuario getRegistradoPor() {
        return registradoPor;
    }

    public Instant getRegistradoEm() {
        return registradoEm;
    }
}
