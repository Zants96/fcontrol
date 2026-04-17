package br.com.lesnik.fcontrol.service;

import br.com.lesnik.fcontrol.dto.LancamentoDTO;
import br.com.lesnik.fcontrol.model.Categoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private LancamentoService lancamentoService;

    @InjectMocks
    private ExportService exportService;

    @Test
    @DisplayName("Deve incluir coluna Mês no CSV quando a exportação for de um ano inteiro")
    void deveIncluirMesNoCsvGeral() {
        int ano = 2026;
        LancamentoDTO l = LancamentoDTO.builder()
                .ano(ano).mes(1).categoria(Categoria.RECEITA)
                .subcategoria("Salário").descricao("Mensal").valor(new BigDecimal("5000.00"))
                .build();

        when(lancamentoService.listarPorAno(ano)).thenReturn(List.of(l));

        byte[] csvBytes = exportService.exportarCsv(ano, 0, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csv).contains("Ano;Mes;Categoria;Subcategoria;Descricao;Valor");
        assertThat(csv).contains("2026;Janeiro;RECEITA;Salário;Mensal;5000,00");
    }

    @Test
    @DisplayName("Deve remover coluna Mês no CSV quando a exportação for de um mês específico")
    void deveRemoverMesNoCsvMensal() {
        int ano = 2026;
        int mes = 4;
        LancamentoDTO l = LancamentoDTO.builder()
                .ano(ano).mes(mes).categoria(Categoria.GASTO)
                .subcategoria("Aluguel").descricao("Apartamento").valor(new BigDecimal("2000.00"))
                .build();

        when(lancamentoService.listarPorAnoEMes(ano, mes)).thenReturn(List.of(l));

        byte[] csvBytes = exportService.exportarCsv(ano, mes, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csv).contains("Ano;Categoria;Subcategoria;Descricao;Valor");
        assertThat(csv).doesNotContain("Mes;");
        assertThat(csv).contains("2026;GASTO;Aluguel;Apartamento;2000,00");
    }

    @Test
    @DisplayName("Deve gerar PDF sem erros")
    void deveGerarPdfSemErros() {
        int ano = 2026;
        when(lancamentoService.listarPorAno(anyInt())).thenReturn(Collections.emptyList());

        byte[] pdfBytes = exportService.exportarPdf(ano, 0, null);
        
        assertThat(pdfBytes).isNotEmpty();
        // Verificar assinatura de arquivo PDF (%PDF-)
        assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");
    }
}
