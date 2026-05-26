package br.com.lesnik.mytwocents.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "investimento_lancamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestimentoLancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ativo_id", nullable = false)
    private Ativo ativo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(15)")
    private TipoOperacao tipoOperacao;

    /** Data da operação */
    @Column(nullable = false)
    private LocalDate data;

    /** Quantidade comprada/vendida (0 para dividendos) */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantidade;

    /** Preço por unidade na operação */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precoUnitario;

    /** Taxas e emolumentos (opcional) */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal custos;

    /** Valor total da operação: (qtd × preço) + custos */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column
    private Long lancamentoFinanceiroId;

    @Column(updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        if (this.custos == null) this.custos = BigDecimal.ZERO;
    }
}
