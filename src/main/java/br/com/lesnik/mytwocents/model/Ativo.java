package br.com.lesnik.mytwocents.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ativo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código do ativo (BBAS3, RZTR11, XRP, etc.) */
    @Column(nullable = false, unique = true)
    private String ticker;

    /** Nome descritivo do ativo (opcional) */
    @Column
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private TipoAtivo tipoAtivo;

    /** Quantidade total em carteira */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantidade;

    /** Preço médio de compra */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precoMedio;

    /** Cotação atual (manual ou via BrAPI) */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precoAtual;

    /** Meta de alocação (%) na carteira */
    @Column(precision = 5, scale = 2)
    private BigDecimal metaPercent;

    /** Se o ativo está ativo na carteira (quantidade > 0) */
    @Column(nullable = false)
    private boolean ativo;

    /** Total de dividendos recebidos para este ativo */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal dividendosTotal;

    /** URL do logotipo do ativo */
    @Column(length = 500)
    private String logoUrl;

    /** Setor do ativo */
    @Column(length = 100)
    private String sector;

    /** Nome completo do ativo */
    @Column(length = 200)
    private String longName;

    @Column
    private java.time.LocalDate dataVencimento;

    @Column(length = 50)
    private String indexador;

    @Column(precision = 15, scale = 4)
    private BigDecimal taxa;

    @Column(updatable = false)
    private LocalDateTime criadoEm;

    @Column
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
        if (this.quantidade == null) this.quantidade = BigDecimal.ZERO;
        if (this.precoMedio == null) this.precoMedio = BigDecimal.ZERO;
        if (this.precoAtual == null) this.precoAtual = BigDecimal.ZERO;
        if (this.dividendosTotal == null) this.dividendosTotal = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
