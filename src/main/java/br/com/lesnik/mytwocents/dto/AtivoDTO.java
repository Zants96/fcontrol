package br.com.lesnik.mytwocents.dto;

import br.com.lesnik.mytwocents.model.TipoAtivo;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtivoDTO {
    private Long id;
    private String ticker;
    private String nome;
    private TipoAtivo tipoAtivo;
    private BigDecimal quantidade;
    private BigDecimal precoMedio;
    private BigDecimal precoAtual;
    private BigDecimal valorTotal;
    private BigDecimal variacao;
    private BigDecimal lucro;
    private BigDecimal percentCarteira;
    private BigDecimal metaPercent;
    private BigDecimal dividendosTotal;
    private boolean ativo;
    private String logoUrl;
    private String sector;
    private String longName;
    private java.time.LocalDate dataVencimento;
    private String indexador;
    private BigDecimal taxa;
    private BigDecimal rendimentoMensal;
}
