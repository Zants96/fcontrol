package br.com.lesnik.fcontrol.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardDTO {

    private Integer ano;

    /** Total anual de receitas */
    private BigDecimal totalReceitas;

    /** Total anual de gastos */
    private BigDecimal totalGastos;

    /** Total anual de assinaturas */
    private BigDecimal totalAssinaturas;

    /** Saldo anual = receitas - gastos - assinaturas */
    private BigDecimal saldoAnual;

    /** Totais de receitas por mês [1..12] */
    private List<BigDecimal> receitasPorMes;

    /** Totais de gastos por mês [1..12] */
    private List<BigDecimal> gastosPorMes;

    /** Totais de assinaturas por mês [1..12] */
    private List<BigDecimal> assinaturasPorMes;

    /** Saldo mensal [1..12] */
    private List<BigDecimal> saldoPorMes;

    /** Gastos por subcategoria no ano (para gráfico donut) */
    private Map<String, BigDecimal> gastosPorSubcategoria;

    /** Os top-5 maiores gastos do ano */
    private List<SubcategoriaValor> topGastos;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SubcategoriaValor {
        private String subcategoria;
        private BigDecimal valor;
    }
}
