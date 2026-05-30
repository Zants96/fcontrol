package br.com.lesnik.mytwocents.dto;

import br.com.lesnik.mytwocents.model.TipoAtivo;
import br.com.lesnik.mytwocents.model.TipoOperacao;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestimentoLancamentoDTO {
    private Long id;
    private Long ativoId;
    private String ticker;
    private TipoAtivo tipoAtivo;
    private TipoOperacao tipoOperacao;
    private LocalDate data;
    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal custos;
    private BigDecimal valorTotal;
    private BigDecimal valorLiquido;
    private LocalDate dataVencimento;
    private String indexador;
    private BigDecimal taxa;
    private String tipoProvento;
}
