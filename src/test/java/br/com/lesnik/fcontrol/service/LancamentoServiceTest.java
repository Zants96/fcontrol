package br.com.lesnik.fcontrol.service;

import br.com.lesnik.fcontrol.dto.DashboardDTO;
import br.com.lesnik.fcontrol.model.Categoria;
import br.com.lesnik.fcontrol.model.Lancamento;
import br.com.lesnik.fcontrol.repository.LancamentoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LancamentoServiceTest {

    @Mock
    private LancamentoRepository repository;

    @InjectMocks
    private LancamentoService service;

    @Test
    @DisplayName("Deve calcular dashboard corretamente para o ano")
    void deveCalcularDashboardAnual() {
        int ano = 2026;

        // Mocking sums for all months
        when(repository.sumByAnoAndMesAndCategoria(eq(ano), anyInt(), eq(Categoria.RECEITA)))
                .thenReturn(new BigDecimal("1000.00"));
        when(repository.sumByAnoAndMesAndCategoriaIn(eq(ano), anyInt(), eq(Arrays.asList(Categoria.GASTO, Categoria.GASTO_FIXO))))
                .thenReturn(new BigDecimal("400.00"));
        when(repository.sumByAnoAndMesAndCategoria(eq(ano), anyInt(), eq(Categoria.ASSINATURA)))
                .thenReturn(new BigDecimal("100.00"));

        // Mocking total sums
        when(repository.sumByAnoAndCategoria(ano, Categoria.RECEITA)).thenReturn(new BigDecimal("12000.00"));
        when(repository.sumByAnoAndCategoriaIn(ano, Arrays.asList(Categoria.GASTO, Categoria.GASTO_FIXO))).thenReturn(new BigDecimal("4800.00"));
        when(repository.sumByAnoAndCategoria(ano, Categoria.ASSINATURA)).thenReturn(new BigDecimal("1200.00"));

        // Mocking finds for top 5 calculation
        Lancamento l1 = Lancamento.builder().subcategoria("Alimentação").valor(new BigDecimal("2000.00")).build();
        Lancamento l2 = Lancamento.builder().subcategoria("Luz").valor(new BigDecimal("500.00")).build();
        when(repository.findByAnoAndCategoriaOrderByMesAscSubcategoriaAsc(ano, Categoria.GASTO)).thenReturn(Arrays.asList(l1, l2));
        when(repository.findByAnoAndCategoriaOrderByMesAscSubcategoriaAsc(ano, Categoria.GASTO_FIXO)).thenReturn(Collections.emptyList());
        when(repository.findByAnoAndCategoriaOrderByMesAscSubcategoriaAsc(ano, Categoria.ASSINATURA)).thenReturn(Collections.emptyList());

        DashboardDTO dashboard = service.calcularDashboard(ano);

        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getAno()).isEqualTo(ano);
        assertThat(dashboard.getTotalReceitas()).isEqualByComparingTo("12000.00");
        assertThat(dashboard.getTotalGastos()).isEqualByComparingTo("4800.00");
        assertThat(dashboard.getTotalAssinaturas()).isEqualByComparingTo("1200.00");
        assertThat(dashboard.getSaldoAnual()).isEqualByComparingTo("6000.00"); // 12000 - 4800 - 1200
        
        assertThat(dashboard.getReceitasPorMes()).hasSize(12).allSatisfy(v -> assertThat(v).isEqualByComparingTo("1000.00"));
        assertThat(dashboard.getTopGastos()).hasSize(2);
        assertThat(dashboard.getTopGastos().get(0).getSubcategoria()).isEqualTo("Alimentação");
        assertThat(dashboard.getTopGastos().get(0).getValor()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Deve retornar saldo zero quando não houver lançamentos")
    void deveRetornarTudoZeroQuandoVazio() {
        int ano = 2026;

        when(repository.sumByAnoAndMesAndCategoria(anyInt(), anyInt(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.sumByAnoAndMesAndCategoriaIn(anyInt(), anyInt(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.sumByAnoAndCategoria(anyInt(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.sumByAnoAndCategoriaIn(anyInt(), any())).thenReturn(BigDecimal.ZERO);

        DashboardDTO dashboard = service.calcularDashboard(ano);

        assertThat(dashboard.getTotalReceitas()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dashboard.getSaldoAnual()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
