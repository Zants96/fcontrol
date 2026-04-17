package br.com.lesnik.fcontrol.dto;

import br.com.lesnik.fcontrol.model.Categoria;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LancamentoDTO {
    private Long id;
    private String descricao;
    private Categoria categoria;
    private String subcategoria;
    private BigDecimal valor;
    private Integer mes;
    private Integer ano;

    /** Número de vezes que o lançamento deve se repetir (usado na criação) */
    private Integer parcelas;

    private Integer parcelaActual;
    private Integer totalParcelas;
    private String grupoId;
}
