package br.com.lesnik.mytwocents.dto;

import br.com.lesnik.mytwocents.model.CategoriaTatica;
import br.com.lesnik.mytwocents.model.TipoAtivo;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TacticalOpportunityDTO {

    private Long ativoId;
    private String ticker;
    private String nome;
    private TipoAtivo tipoAtivo;
    private CategoriaTatica categoriaTatica;
    private boolean ciclico;
    private boolean estrutural;
    private BigDecimal precoAtual;
    private BigDecimal precoMedio;
    private BigDecimal variacaoPercent;
    private String tipoGatilho; // "COMPRA" ou "VENDA"
    private int nivelGatilho;   // 1, 2, 3...
    private String sugestaoAcao;
    private BigDecimal sugestaoQuantidade;
}
