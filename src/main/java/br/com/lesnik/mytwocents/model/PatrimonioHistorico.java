package br.com.lesnik.mytwocents.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "patrimonio_historico", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"ano", "mes"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatrimonioHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer ano;

    @Column(nullable = false)
    private Integer mes;

    @Column(name = "valor_investido", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorInvestido;

    @Column(name = "valor_patrimonio", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorPatrimonio;

    @Column(name = "valor_lucro", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorLucro;

    @Column(name = "valor_dividendos", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorDividendos;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
