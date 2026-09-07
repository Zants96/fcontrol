package br.com.lesnik.mytwocents.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AporteCalculoResponseDTO {

    private BigDecimal valorAporteSolicitado;
    private BigDecimal valorAporteEfetivo;
    private BigDecimal sobraCaixa;
    private boolean emergencyLockAtivo;
    private String mensagemLock;
    private List<AporteItemDTO> itens;
}
