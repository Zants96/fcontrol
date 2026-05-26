package br.com.lesnik.mytwocents.dto;

import br.com.lesnik.mytwocents.model.TipoAtivo;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestimentoDashboardDTO {

    /** Soma de (quantidade × precoAtual) de todos os ativos */
    private BigDecimal patrimonioTotal;

    /** Soma de todas as compras - vendas */
    private BigDecimal valorInvestido;

    /** patrimônio - valorInvestido */
    private BigDecimal lucroTotal;

    /** Soma de todos os dividendos recebidos */
    private BigDecimal dividendosTotal;

    /** Variação percentual: (patrimônio - valorInvestido) / valorInvestido × 100 */
    private BigDecimal variacaoPercent;

    /** Evolução Patrimonial 12 meses: Total acumulado investido no mês */
    private List<BigDecimal> evolucaoInvestido12m;

    /** Evolução Patrimonial 12 meses: Patrimônio aproximado no mês (baseado no preço atual) */
    private List<BigDecimal> evolucaoPatrimonio12m;

    /** Evolução de Dividendos 12 meses */
    private List<BigDecimal> evolucaoDividendos12m;

    /** Labels para os gráficos de 12 meses (ex: "Jan", "Fev") */
    private List<String> labels12m;

    /** Distribuição por tipo: TipoAtivo → valor total */
    private Map<TipoAtivo, BigDecimal> distribuicaoPorTipo;

    /** Ativos agrupados por tipo */
    private Map<TipoAtivo, List<AtivoDTO>> ativosPorTipo;

    /** Resumo por tipo para os cabeçalhos do acordeão */
    private Map<TipoAtivo, TipoResumo> resumoPorTipo;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TipoResumo {
        private int quantidadeAtivos;
        private BigDecimal valorTotal;
        private BigDecimal variacao;
        private BigDecimal percentCarteira;
        private BigDecimal metaPercent;
    }
}
