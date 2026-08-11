package br.com.lesnik.mytwocents.dto;

import br.com.lesnik.mytwocents.model.TipoAtivo;
import br.com.lesnik.mytwocents.model.TipoOperacao;
import jakarta.validation.constraints.*;
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

    @NotBlank(message = "O ticker do ativo é obrigatório")
    @Size(max = 20, message = "Ticker inválido")
    private String ticker;

    @NotNull(message = "O tipo do ativo é obrigatório")
    private TipoAtivo tipoAtivo;

    @NotNull(message = "O tipo de operação é obrigatório")
    private TipoOperacao tipoOperacao;

    @NotNull(message = "A data da operação é obrigatória")
    private LocalDate data;

    @NotNull(message = "A quantidade é obrigatória")
    @Positive(message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @NotNull(message = "O preço unitário é obrigatório")
    @PositiveOrZero(message = "O preço unitário não pode ser negativo")
    private BigDecimal precoUnitario;
    private BigDecimal custos;
    private BigDecimal valorTotal;
    private BigDecimal valorLiquido;
    private LocalDate dataVencimento;
    private String indexador;
    private BigDecimal taxa;
    private String tipoProvento;
}
