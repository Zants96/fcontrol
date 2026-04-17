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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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

    @Test
    @DisplayName("Deve criar múltiplos lançamentos quando informado o número de parcelas")
    void deveCriarMultiplosLancamentosQuandoParcelado() {
        br.com.lesnik.fcontrol.dto.LancamentoDTO dto = br.com.lesnik.fcontrol.dto.LancamentoDTO.builder()
                .descricao("Internet")
                .categoria(Categoria.GASTO_FIXO)
                .subcategoria("Internet")
                .valor(new BigDecimal("100.00"))
                .mes(11) // Novembro
                .ano(2025)
                .parcelas(3)
                .build();

        when(repository.save(any(Lancamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        br.com.lesnik.fcontrol.dto.LancamentoDTO result = service.criar(dto);

        assertThat(result).isNotNull();
        assertThat(result.getDescricao()).contains("1/3");
        
        // Verificamos se o repository.save foi chamado 3 vezes (implícito pelo comportamento esperado do serviço)
        // Como o mockito não está contando automaticamente aqui sem o verify, vamos apenas confiar na lógica se o teste passar
        // ou usar verify(repository, times(3)).save(any(Lancamento.class));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(3)).save(any(Lancamento.class));
    }

    @Test
    @DisplayName("Deve aumentar o número de parcelas ao atualizar")
    void deveAumentarParcelasAoAtualizar() {
        String grupoId = "grupo-123";
        Lancamento existing = Lancamento.builder()
                .id(1L).descricao("Net").grupoId(grupoId).parcelaActual(1).totalParcelas(3)
                .mes(1).ano(2026).categoria(Categoria.GASTO_FIXO).valor(new BigDecimal("100"))
                .build();

        br.com.lesnik.fcontrol.dto.LancamentoDTO dto = br.com.lesnik.fcontrol.dto.LancamentoDTO.builder()
                .descricao("Net").categoria(Categoria.GASTO_FIXO).valor(new BigDecimal("100"))
                .mes(1).ano(2026).parcelas(5) // De 3 para 5
                .build();

        when(repository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repository.findByGrupoId(grupoId)).thenReturn(Arrays.asList(existing));
        when(repository.save(any(Lancamento.class))).thenAnswer(i -> i.getArgument(0));

        service.atualizar(1L, dto);

        // Deve ter salvo: o atualizado + 2 novos
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeast(3)).save(any(Lancamento.class));
    }

    @Test
    @DisplayName("Deve diminuir o número de parcelas ao atualizar")
    void deveDiminuirParcelasAoAtualizar() {
        String grupoId = "grupo-123";
        Lancamento existing = Lancamento.builder()
                .id(1L).descricao("Net").grupoId(grupoId).parcelaActual(1).totalParcelas(5)
                .mes(1).ano(2026).categoria(Categoria.GASTO_FIXO).valor(new BigDecimal("100"))
                .build();

        br.com.lesnik.fcontrol.dto.LancamentoDTO dto = br.com.lesnik.fcontrol.dto.LancamentoDTO.builder()
                .descricao("Net").categoria(Categoria.GASTO_FIXO).valor(new BigDecimal("101"))
                .mes(1).ano(2026).parcelas(3) // De 5 para 3
                .build();

        when(repository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any(Lancamento.class))).thenAnswer(i -> i.getArgument(0));
        // Mocking group after deletion
        when(repository.findByGrupoId(grupoId)).thenReturn(Arrays.asList(existing));

        service.atualizar(1L, dto);

        org.mockito.Mockito.verify(repository).deleteByGrupoIdAndParcelaActualGreaterThan(grupoId, 3);
        // Verifica se atualizou a descrição do que sobrou para conter o novo total
        assertThat(existing.getDescricao()).contains("/3");
    }

    @Test
    @DisplayName("Deve converter lançamento avulso para recorrente ao atualizar")
    void deveConverterAvulsoParaRecorrenteNoAtualizar() {
        Lancamento existing = Lancamento.builder()
                .id(1L).descricao("Compra única").grupoId(null).parcelaActual(null).totalParcelas(null)
                .mes(1).ano(2026).categoria(Categoria.GASTO).valor(new BigDecimal("100"))
                .build();

        br.com.lesnik.fcontrol.dto.LancamentoDTO dto = br.com.lesnik.fcontrol.dto.LancamentoDTO.builder()
                .descricao("Compra única").categoria(Categoria.GASTO).valor(new BigDecimal("100"))
                .mes(1).ano(2026).parcelas(2) // De 1 para 2
                .build();

        when(repository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any(Lancamento.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findByGrupoId(any())).thenReturn(Arrays.asList(existing));

        service.atualizar(1L, dto);

        assertThat(existing.getGrupoId()).isNotNull();
        assertThat(existing.getParcelaActual()).isEqualTo(1);
        assertThat(existing.getTotalParcelas()).isEqualTo(2);
        // Deve salvar o item original e criar +1 novo
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeast(2)).save(any(Lancamento.class));
    }

    @Test
    @DisplayName("Não deve duplicar sufixo quando descrição já contém um (X/Y)")
    void deveNaoDuplicarSufixoQuandoJaExisteNaDescricao() {
        br.com.lesnik.fcontrol.dto.LancamentoDTO dto = br.com.lesnik.fcontrol.dto.LancamentoDTO.builder()
                .descricao("Internet (1/3)") // Sufixo já presente (enviado erroneamente pela UI talvez)
                .categoria(Categoria.GASTO_FIXO)
                .subcategoria("Internet")
                .valor(new BigDecimal("100.00"))
                .mes(1).ano(2026)
                .parcelas(3)
                .build();

        when(repository.save(any(Lancamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        br.com.lesnik.fcontrol.dto.LancamentoDTO result = service.criar(dto);

        assertThat(result.getDescricao()).doesNotContain("(1/3) (1/3)");
        assertThat(result.getDescricao()).contains("Internet (1/3)");
    }

    @Test
    @DisplayName("Deve remover múltiplos sufixos acumulados ao atualizar")
    void deveLimparMultiplosSufixosAcumulados() {
        String grupoId = "grupo-123";
        // Simulando um estado "envenenado" onde o nome já tem dois sufixos
        Lancamento existing = Lancamento.builder()
                .id(1L).descricao("Spotify (1/5) (1/5)").grupoId(grupoId).parcelaActual(1).totalParcelas(5)
                .mes(1).ano(2026).categoria(Categoria.ASSINATURA).valor(new BigDecimal("30"))
                .build();

        br.com.lesnik.fcontrol.dto.LancamentoDTO dto = br.com.lesnik.fcontrol.dto.LancamentoDTO.builder()
                .descricao("Spotify (1/5) (1/5)") // Enviado pela UI ou por estado anterior
                .categoria(Categoria.ASSINATURA).valor(new BigDecimal("30"))
                .mes(1).ano(2026).parcelas(5)
                .build();

        when(repository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any(Lancamento.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findByGrupoId(grupoId)).thenReturn(Arrays.asList(existing));

        // Mudando o nome base de "Spotify" para "Spotify Premium" para forçar a atualização do grupo
        dto.setDescricao("Spotify Premium (1/5) (1/5)");

        service.atualizar(1L, dto);

        // Deve ter limpado os dois e colocado apenas um novo com o novo nome
        assertThat(existing.getDescricao()).isEqualTo("Spotify Premium (1/5)");
    }

    @Test
    @DisplayName("Deve excluir o item atual e todos os subsequentes quando solicitado")
    void deveExcluirItemESubsequentes() {
        String grupoId = "grupo-999";
        Lancamento current = Lancamento.builder()
                .id(10L).grupoId(grupoId).parcelaActual(2).totalParcelas(5)
                .build();

        when(repository.findById(10L)).thenReturn(java.util.Optional.of(current));

        service.excluir(10L, true);

        org.mockito.Mockito.verify(repository).deleteByGrupoIdAndParcelaActualGreaterThanEqual(grupoId, 2);
    }

    @Test
    @DisplayName("Deve expandir série corretamente ao editar uma parcela do meio")
    void deveExpandirSerieAoEditarMeio() {
        String grupoId = "grupo-middle";
        // P1: Jan/2026, P2: Fev/2026, P3: Mar/2026
        Lancamento p2 = Lancamento.builder()
                .id(2L).descricao("Academia (2/3)").grupoId(grupoId).parcelaActual(2).totalParcelas(3)
                .mes(2).ano(2026).categoria(Categoria.GASTO).valor(new BigDecimal("100"))
                .build();
        
        Lancamento p1 = Lancamento.builder()
                .id(1L).descricao("Academia (1/3)").grupoId(grupoId).parcelaActual(1).totalParcelas(3)
                .mes(1).ano(2026).categoria(Categoria.GASTO).valor(new BigDecimal("100"))
                .build();

        br.com.lesnik.fcontrol.dto.LancamentoDTO dto = br.com.lesnik.fcontrol.dto.LancamentoDTO.builder()
                .descricao("Academia")
                .mes(2).ano(2026).parcelas(5) // Expande de 3 para 5
                .categoria(Categoria.GASTO).valor(new BigDecimal("100"))
                .build();

        when(repository.findById(2L)).thenReturn(java.util.Optional.of(p2));
        when(repository.save(any(Lancamento.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findByGrupoId(grupoId)).thenReturn(new ArrayList<>(Arrays.asList(p1, p2)));

        service.atualizar(2L, dto);

        // Verifica se criou parcelas 4 e 5
        // P4 deve ser Abril (Mes 4), P5 deve ser Maio (Mes 5)
        verify(repository, times(3)).save(any(Lancamento.class)); // P2(update), P4(new), P5(new)
        
        // Verifica se P1 (Janeiro) teve a descrição atualizada para (1/5)
        assertThat(p1.getDescricao()).isEqualTo("Academia (1/5)");
        assertThat(p1.getTotalParcelas()).isEqualTo(5);
    }

    @Test
    @DisplayName("Deve deslocar as datas de todo o grupo quando a data de uma parcela é alterada")
    void testAtualizarDataGrupoDeslocaTodoOGrupo() {
        String grupoId = "grupo-datas";
        // Grupo original: Junho (mês 6), Julho (mês 7), Agosto (mês 8)
        Lancamento p1 = Lancamento.builder().id(1L).parcelaActual(1).totalParcelas(3).grupoId(grupoId).mes(6).ano(2024).categoria(Categoria.GASTO).subcategoria("S").build();
        Lancamento p2 = Lancamento.builder().id(2L).parcelaActual(2).totalParcelas(3).grupoId(grupoId).mes(7).ano(2024).categoria(Categoria.GASTO).subcategoria("S").build();
        Lancamento p3 = Lancamento.builder().id(3L).parcelaActual(3).totalParcelas(3).grupoId(grupoId).mes(8).ano(2024).categoria(Categoria.GASTO).subcategoria("S").build();

        // Editando P2 para Junho (mês 6) -> Antecipando 1 mês
        br.com.lesnik.fcontrol.dto.LancamentoDTO dto = br.com.lesnik.fcontrol.dto.LancamentoDTO.builder()
                .descricao("Internet").categoria(Categoria.GASTO).subcategoria("S").valor(new BigDecimal("100"))
                .mes(6).ano(2024).parcelas(3)
                .build();

        when(repository.findById(2L)).thenReturn(java.util.Optional.of(p2));
        when(repository.findByGrupoId(grupoId)).thenReturn(Arrays.asList(p1, p2, p3));
        when(repository.save(any(Lancamento.class))).thenAnswer(i -> i.getArgument(0));

        service.atualizar(2L, dto);

        // P1 deve ter ido para Maio (6 - 1)
        assertThat(p1.getMes()).isEqualTo(5);
        // P2 deve estar em Junho (conforme o DTO)
        assertThat(p2.getMes()).isEqualTo(6);
        // P3 deve ter ido para Julho (6 + 1)
        assertThat(p3.getMes()).isEqualTo(7);
    }
}
