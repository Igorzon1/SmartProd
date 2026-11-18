package br.com.smartprod.producao;

import java.time.Instant;

import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import br.com.smartprod.shared.ConflitoException;
import br.com.smartprod.shared.DadosInvalidosException;
import jakarta.persistence.Version;

@Entity
@Table(name = "ordens_producao")
public class OrdemProducao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quantidade_unidades", nullable = false)
    private Integer quantidadeUnidades;

    @Column(name = "horario_entrega", nullable = false)
    private Instant horarioEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOrdemProducao status = StatusOrdemProducao.PENDENTE;

    @Column(name = "iniciada_em")
    private Instant iniciadaEm;

    @Column(name = "concluida_em")
    private Instant concluidaEm;

    @Column(name = "quantidade_produzida")
    private Integer quantidadeProduzida;

    /** Controle de concorrência: se duas pessoas alteram a mesma OP ao mesmo tempo, a segunda recebe 409. */
    @Version
    @Column(nullable = false)
    private long versao;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ficha_tecnica_id", nullable = false)
    private FichaTecnica fichaTecnica;

    @ManyToOne(optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Usuario responsavel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private Usuario criadoPor;

    protected OrdemProducao() {
    }

    public OrdemProducao(
            FichaTecnica fichaTecnica,
            Integer quantidadeUnidades,
            Instant horarioEntrega,
            Usuario responsavel,
            Usuario criadoPor) {

        if (quantidadeUnidades == null || quantidadeUnidades <= 0) {
            throw new DadosInvalidosException(
                    "A quantidade de unidades deve ser maior que zero.");
        }

        if (horarioEntrega == null) {
            throw new DadosInvalidosException(
                    "O horário de entrega é obrigatório.");
        }

        if (responsavel == null || criadoPor == null) {
            throw new DadosInvalidosException(
                    "O responsável e o criador da ordem são obrigatórios.");
        }

        if (fichaTecnica == null) {
            throw new DadosInvalidosException(
                    "A ficha técnica é obrigatória.");
        }

        this.fichaTecnica = fichaTecnica;
        this.quantidadeUnidades = quantidadeUnidades;
        this.horarioEntrega = horarioEntrega;
        this.responsavel = responsavel;
        this.criadoPor = criadoPor;
    }

    public Long getId() {
        return id;
    }

    public Integer getQuantidadeUnidades() {
        return quantidadeUnidades;
    }

    public Instant getHorarioEntrega() {
        return horarioEntrega;
    }

    public StatusOrdemProducao getStatus() {
        return status;
    }

    public Instant getIniciadaEm() {
        return iniciadaEm;
    }

    public Instant getConcluidaEm() {
        return concluidaEm;
    }

    public Integer getQuantidadeProduzida() {
        return quantidadeProduzida;
    }

    public Usuario getResponsavel() {
        return responsavel;
    }

    public Usuario getCriadoPor() {
        return criadoPor;
    }

    public FichaTecnica getFichaTecnica() {
        return fichaTecnica;
    }

    public void iniciar() {
        if (this.status != StatusOrdemProducao.PENDENTE) {
            throw new ConflitoException(
                    "Somente ordens pendentes podem ser iniciadas.");
        }

        this.status = StatusOrdemProducao.EM_ANDAMENTO;
        this.iniciadaEm = Instant.now();
    }

    public void concluir(Integer quantidadeProduzida) {
        if (this.status != StatusOrdemProducao.EM_ANDAMENTO) {
            throw new ConflitoException(
                    "Somente ordens em andamento podem ser concluídas.");
        }

        if (quantidadeProduzida == null || quantidadeProduzida < 0) {
            throw new DadosInvalidosException(
                    "A quantidade produzida não pode ser nula ou negativa.");
        }

        this.quantidadeProduzida = quantidadeProduzida;
        this.status = StatusOrdemProducao.CONCLUIDA;
        this.concluidaEm = Instant.now();
    }

}