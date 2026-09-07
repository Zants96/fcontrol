package br.com.lesnik.mytwocents.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AporteCalculoRequestDTO {

    @NotNull(message = "O valor do aporte é obrigatório")
    @Positive(message = "O valor do aporte deve ser positivo")
    private BigDecimal valorAporte;
}
