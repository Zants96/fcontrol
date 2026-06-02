package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.dto.LancamentoDTO;
import br.com.lesnik.mytwocents.model.Categoria;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final LancamentoService lancamentoService;
    private final InvestimentoService investimentoService;

    public static final String[] MESES = {
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    };

    public byte[] exportarCsv(Integer ano, Integer mes, Categoria categoria) {
        List<LancamentoDTO> lancamentos = buscarLancamentos(ano, mes, categoria);
        StringBuilder sb = new StringBuilder();

        // Se o mês for 0 (geral/dashboard), incluímos a coluna mês para diferenciar os registros.
        // Se for um mês específico, a coluna é removida pois o mês já consta no título do arquivo.
        boolean incluirMes = (mes == null || mes <= 0);


        // BOM para Excel identificar como UTF-8
        sb.append("\ufeff");

        // Header condicional: inclui Mês apenas quando é exportação geral
        if (incluirMes) {
            sb.append("Ano;Mes;Dia;Categoria;Subcategoria;Descricao;Valor\n");
        } else {
            sb.append("Ano;Dia;Categoria;Subcategoria;Descricao;Valor\n");
        }

        for (LancamentoDTO l : lancamentos) {
            sb.append(l.getAno()).append(";");
            if (incluirMes) sb.append(MESES[l.getMes() - 1]).append(";");
            sb.append(l.getDia() != null ? String.format("%02d", l.getDia()) : "").append(";");
            sb.append(l.getCategoria()).append(";")
                    .append(sanitizarCampoCsv(l.getSubcategoria())).append(";")
                    .append(sanitizarCampoCsv(l.getDescricao())).append(";")
                    .append(l.getValor().toString().replace(".", ","))
                    .append("\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String sanitizarCampoCsv(String valor) {
        if (valor == null) return "";
        String limpo = valor.replace(";", ",");
        // Previne injeção de fórmulas no Excel (CSV Injection)
        if (limpo.startsWith("=") || limpo.startsWith("+") || limpo.startsWith("-") || limpo.startsWith("@")) {
            return "'" + limpo;
        }
        return limpo;
    }

    public byte[] exportarPdf(Integer ano, Integer mes, Categoria categoria) {
        List<LancamentoDTO> lancamentos = buscarLancamentos(ano, mes, categoria);
        boolean incluirMes = (mes == null || mes <= 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);

        document.open();

        // Fontes
        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
        Font fontSub = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY);
        Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font fontRow = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        // Título
        Paragraph title = new Paragraph("MyTwoCents - Relatório Financeiro", fontTitle);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        String subtitleText = "Exercício: " + ano;
        if (!incluirMes) subtitleText += " - " + MESES[mes - 1];
        if (categoria != null) subtitleText += " (" + formatarCategoria(categoria) + ")";

        Paragraph subtitle = new Paragraph(subtitleText, fontSub);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);

        // Ajuste dinâmico da tabela: se incluir o mês, a tabela tem 5 colunas, caso contrário, 4.
        // Isso otimiza o espaço horizontal no PDF para as descrições.
        PdfPTable table;
        if (incluirMes) {
            table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 2f, 1.2f, 2.5f, 4f, 2.5f});
            addTableHeader(table, fontHeader, "Mês");
        } else {
            table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 1.2f, 2.5f, 4.5f, 2.5f});
        }
        addTableHeader(table, fontHeader, "Tipo");
        addTableHeader(table, fontHeader, "Dia");
        addTableHeader(table, fontHeader, "Subcategoria");
        addTableHeader(table, fontHeader, "Descrição");
        addTableHeader(table, fontHeader, "Valor");

        BigDecimal totalReceitas = BigDecimal.ZERO;
        BigDecimal totalGastos = BigDecimal.ZERO;

        NumberFormat brFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        Color colorOdd = Color.WHITE;
        Color colorEven = new Color(245, 245, 245); // Cinza bem claro
        int rowIdx = 0;

        for (LancamentoDTO l : lancamentos) {
            Color bgColor = (rowIdx % 2 == 0) ? colorOdd : colorEven;

            if (incluirMes) addRowCell(table, MESES[l.getMes() - 1], fontRow, bgColor, Element.ALIGN_LEFT);
            addRowCell(table, formatarCategoria(l), fontRow, bgColor, Element.ALIGN_LEFT);
            addRowCell(table, l.getDia() != null ? String.format("%02d", l.getDia()) : "-", fontRow, bgColor, Element.ALIGN_LEFT);
            addRowCell(table, l.getSubcategoria(), fontRow, bgColor, Element.ALIGN_LEFT);
            addRowCell(table, l.getDescricao(), fontRow, bgColor, Element.ALIGN_LEFT);
            
            String valorFormatado = brFormat.format(l.getValor());
            addRowCell(table, valorFormatado, fontRow, bgColor, Element.ALIGN_RIGHT);

            if (l.getCategoria() == Categoria.RECEITA) {
                totalReceitas = totalReceitas.add(l.getValor());
            } else if (l.getCategoria() != Categoria.TRANSFERENCIA || 
                       (l.getCategoria() == Categoria.TRANSFERENCIA && !"Outras Transferências".equals(l.getSubcategoria()))) {
                totalGastos = totalGastos.add(l.getValor());
            }
            rowIdx++;
        }

        document.add(table);

        // Resumo Final
        document.add(new Paragraph("\n"));
        Paragraph resumo = new Paragraph("Resumo das Transações", fontTitle);
        resumo.setSpacingBefore(10);
        document.add(resumo);

        if (categoria == null || categoria == Categoria.RECEITA) {
            document.add(new Paragraph("Total Receitas: " + brFormat.format(totalReceitas), fontRow));
        }
        
        if (categoria == null || categoria != Categoria.RECEITA) {
            document.add(new Paragraph("Total Despesas: " + brFormat.format(totalGastos), fontRow));
        }
        
        if (categoria == null) {
            BigDecimal saldo = totalReceitas.subtract(totalGastos);
            Font fontSaldo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, saldo.compareTo(BigDecimal.ZERO) >= 0 ? new Color(16, 185, 129) : Color.RED);
            document.add(new Paragraph("Saldo Final: " + brFormat.format(saldo), fontSaldo));
        }

        document.close();
        return out.toByteArray();
    }

    private String formatarCategoria(Categoria cat) {
        return switch (cat) {
            case RECEITA -> "Receita";
            case GASTO -> "Gasto";
            case GASTO_FIXO -> "Gasto Fixo";
            case ASSINATURA -> "Assinatura";
            case TRANSFERENCIA -> "Aporte";
        };
    }

    private String formatarCategoria(LancamentoDTO l) {
        if (l.getCategoria() == Categoria.TRANSFERENCIA) {
            return "Aporte";
        }
        return formatarCategoria(l.getCategoria());
    }

    private List<LancamentoDTO> buscarLancamentos(Integer ano, Integer mes, Categoria categoria) {
        if (mes != null && mes > 0 && categoria != null) {
            return lancamentoService.listarPorAnoMesECategoria(ano, mes, categoria);
        } else if (mes != null && mes > 0) {
            return lancamentoService.listarPorAnoEMes(ano, mes);
        } else if (categoria != null) {
            return lancamentoService.listarPorAnoECategoria(ano, categoria);
        } else {
            return lancamentoService.listarPorAno(ano);
        }
    }

    private void addTableHeader(PdfPTable table, Font font, String text) {
        PdfPCell header = new PdfPCell();
        header.setBackgroundColor(new Color(17, 24, 39));
        header.setBorderWidth(1);
        header.setPhrase(new Phrase(text, font));
        header.setPadding(5);
        table.addCell(header);
    }

    private void addRowCell(PdfPTable table, String text, Font font, Color bgColor, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(200, 200, 200));
        cell.setPadding(5);
        table.addCell(cell);
    }

    // ─── INVESTIMENTOS ───────────────────────────────────────────────────────

    public byte[] exportarInvestimentosCsv() {
        var dashboard = investimentoService.calcularDashboard();
        StringBuilder sb = new StringBuilder();

        sb.append("\ufeff");
        sb.append("Ticker;Nome;Tipo;Quantidade;Preco Medio;Preco Atual;Valor Total;Percentual Carteira;Lucro/Prejuizo\n");

        for (List<br.com.lesnik.mytwocents.dto.AtivoDTO> ativos : dashboard.getAtivosPorTipo().values()) {
            for (br.com.lesnik.mytwocents.dto.AtivoDTO a : ativos) {
                sb.append(a.getTicker()).append(";")
                  .append(sanitizarCampoCsv(a.getNome())).append(";")
                  .append(a.getTipoAtivo()).append(";")
                  .append(a.getQuantidade().toString().replace(".", ",")).append(";")
                  .append(a.getPrecoMedio().toString().replace(".", ",")).append(";")
                  .append(a.getPrecoAtual().toString().replace(".", ",")).append(";")
                  .append(a.getValorTotal().toString().replace(".", ",")).append(";")
                  .append(a.getPercentCarteira().toString().replace(".", ",")).append(";")
                  .append(a.getLucro().toString().replace(".", ","))
                  .append("\n");
            }
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exportarInvestimentosPdf() {
        var dashboard = investimentoService.calcularDashboard();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, out);
        document.open();

        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
        Font fontSub = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY);
        Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font fontRow = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        Paragraph title = new Paragraph("MyTwoCents - Carteira de Investimentos", fontTitle);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph("Posição Consolidada", fontSub);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 2.5f, 1.5f, 1.5f, 1.5f, 2f, 1.5f, 1.5f});
        
        addTableHeader(table, fontHeader, "Ticker");
        addTableHeader(table, fontHeader, "Nome");
        addTableHeader(table, fontHeader, "Qtd");
        addTableHeader(table, fontHeader, "Preço Médio");
        addTableHeader(table, fontHeader, "Preço Atual");
        addTableHeader(table, fontHeader, "Valor Total");
        addTableHeader(table, fontHeader, "% Cart.");
        addTableHeader(table, fontHeader, "Lucro/Prej.");

        NumberFormat brFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        Color colorOdd = Color.WHITE;
        Color colorEven = new Color(245, 245, 245);
        int rowIdx = 0;

        for (List<br.com.lesnik.mytwocents.dto.AtivoDTO> ativos : dashboard.getAtivosPorTipo().values()) {
            for (br.com.lesnik.mytwocents.dto.AtivoDTO a : ativos) {
                Color bgColor = (rowIdx % 2 == 0) ? colorOdd : colorEven;

                addRowCell(table, a.getTicker(), fontRow, bgColor, Element.ALIGN_LEFT);
                addRowCell(table, a.getNome(), fontRow, bgColor, Element.ALIGN_LEFT);
                addRowCell(table, a.getQuantidade().toString(), fontRow, bgColor, Element.ALIGN_RIGHT);
                addRowCell(table, brFormat.format(a.getPrecoMedio()), fontRow, bgColor, Element.ALIGN_RIGHT);
                addRowCell(table, brFormat.format(a.getPrecoAtual()), fontRow, bgColor, Element.ALIGN_RIGHT);
                addRowCell(table, brFormat.format(a.getValorTotal()), fontRow, bgColor, Element.ALIGN_RIGHT);
                addRowCell(table, a.getPercentCarteira().setScale(2, RoundingMode.HALF_UP) + "%", fontRow, bgColor, Element.ALIGN_RIGHT);
                
                Font fontLucro = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, a.getLucro().compareTo(BigDecimal.ZERO) >= 0 ? new Color(16, 185, 129) : Color.RED);
                PdfPCell cellLucro = new PdfPCell(new Phrase(brFormat.format(a.getLucro()), fontLucro));
                cellLucro.setBackgroundColor(bgColor);
                cellLucro.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cellLucro.setBorderColor(new Color(200, 200, 200));
                cellLucro.setPadding(5);
                table.addCell(cellLucro);

                rowIdx++;
            }
        }

        document.add(table);

        document.add(new Paragraph("\n"));
        Paragraph resumo = new Paragraph("Resumo da Carteira", fontTitle);
        resumo.setSpacingBefore(10);
        document.add(resumo);

        document.add(new Paragraph("Valor Investido: " + brFormat.format(dashboard.getValorInvestido()), fontRow));
        document.add(new Paragraph("Patrimônio Total: " + brFormat.format(dashboard.getPatrimonioTotal()), fontRow));
        
        Font fontLucroTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, dashboard.getLucroTotal().compareTo(BigDecimal.ZERO) >= 0 ? new Color(16, 185, 129) : Color.RED);
        document.add(new Paragraph("Lucro Total: " + brFormat.format(dashboard.getLucroTotal()) + " (" + dashboard.getVariacaoPercent().setScale(2, RoundingMode.HALF_UP) + "%)", fontLucroTotal));

        document.close();
        return out.toByteArray();
    }
}
