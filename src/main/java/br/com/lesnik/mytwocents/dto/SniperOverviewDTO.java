package br.com.lesnik.mytwocents.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SniperOverviewDTO {

    private boolean emergencyLock;
    private BigDecimal totalSeguranca;
    private BigDecimal emergencyBoxTarget;
    private BigDecimal pctSeguranca;
    private BigDecimal patrimonioTotal;
    private BigDecimal valorFaltanteSeguranca;
    private int quantidadeOportunidadesTaticas;
    private List<TacticalOpportunityDTO> oportunidades;
}
