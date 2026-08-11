package br.com.lesnik.mytwocents.dto;

import br.com.lesnik.mytwocents.model.Categoria;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LancamentoDTO {
    private Long id;

    @NotBlank(message = "A descrição não pode ser vazia")
    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
    private String descricao;

    @NotNull(message = "A categoria é obrigatória")
    private Categoria categoria;

    private String subcategoria;

    @NotNull(message = "O valor é obrigatório")
    @Positive(message = "O valor deve ser maior que zero")
    private BigDecimal valor;

    @NotNull(message = "O mês é obrigatório")
    @Min(value = 1, message = "Mês inválido")
    @Max(value = 12, message = "Mês inválido")
    private Integer mes;

    @NotNull(message = "O ano é obrigatório")
    private Integer ano;

    private Integer dia;

    /** Número de vezes que o lançamento deve se repetir (usado na criação) */
    private Integer parcelas;

    private Integer parcelaActual;
    private Integer totalParcelas;
    private String grupoId;
}
