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
public class AporteItemDTO {

    private Long ativoId;
    private String ticker;
    private String nome;
    private CategoriaTatica categoriaTatica;
    private TipoAtivo tipoAtivo;
    private BigDecimal cotasEstimadas;
    private BigDecimal precoAtual;
    private BigDecimal valorAlocado;
    private BigDecimal percentualAporte;
    private BigDecimal deficit;
    private boolean ativoSeguranca;
}
