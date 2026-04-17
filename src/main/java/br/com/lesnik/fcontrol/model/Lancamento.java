package br.com.lesnik.fcontrol.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lancamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private Categoria categoria;

    /**
     * Sub-categoria livre (ex: "Salário", "Netflix", "Moradia")
     */
    @Column(nullable = false)
    private String subcategoria;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    /** Mês do lançamento: 1 = Janeiro, 12 = Dezembro */
    @Column(nullable = false)
    private Integer mes;

    /** Ano do lançamento, ex: 2025 */
    @Column(nullable = false)
    private Integer ano;

    @Column(updatable = false)
    private LocalDateTime criadoEm;

    private Integer parcelaActual;
    private Integer totalParcelas;
    private String grupoId;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
    }
}
