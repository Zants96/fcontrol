package br.com.lesnik.fcontrol.service;

import br.com.lesnik.fcontrol.dto.LancamentoDTO;
import br.com.lesnik.fcontrol.model.Categoria;
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
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final LancamentoService lancamentoService;

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
            sb.append("Ano;Mes;Categoria;Subcategoria;Descricao;Valor\n");
        } else {
            sb.append("Ano;Categoria;Subcategoria;Descricao;Valor\n");
        }

        for (LancamentoDTO l : lancamentos) {
            sb.append(l.getAno()).append(";");
            if (incluirMes) sb.append(MESES[l.getMes() - 1]).append(";");
            sb.append(l.getCategoria()).append(";")
                    .append(l.getSubcategoria()).append(";")
                    .append(l.getDescricao().replace(";", ",")).append(";")
                    .append(l.getValor().toString().replace(".", ","))
                    .append("\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
        Paragraph title = new Paragraph("FControl - Relatório Financeiro", fontTitle);
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
            table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 2f, 2.5f, 4f, 2.5f});
            addTableHeader(table, fontHeader, "Mês");
        } else {
            table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 2.5f, 4.5f, 2.5f});
        }
        addTableHeader(table, fontHeader, "Tipo");
        addTableHeader(table, fontHeader, "Subcategoria");
        addTableHeader(table, fontHeader, "Descrição");
        addTableHeader(table, fontHeader, "Valor");

        BigDecimal totalReceitas = BigDecimal.ZERO;
        BigDecimal totalGastos = BigDecimal.ZERO;

        NumberFormat brFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        for (LancamentoDTO l : lancamentos) {
            if (incluirMes) table.addCell(new Phrase(MESES[l.getMes() - 1], fontRow));
            table.addCell(new Phrase(formatarCategoria(l.getCategoria()), fontRow));
            table.addCell(new Phrase(l.getSubcategoria(), fontRow));
            table.addCell(new Phrase(l.getDescricao(), fontRow));
            
            String valorFormatado = brFormat.format(l.getValor());
            PdfPCell cellValor = new PdfPCell(new Phrase(valorFormatado, fontRow));
            cellValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cellValor);

            if (l.getCategoria().name().equals("RECEITA")) {
                totalReceitas = totalReceitas.add(l.getValor());
            } else {
                totalGastos = totalGastos.add(l.getValor());
            }
        }

        document.add(table);

        // Resumo Final
        document.add(new Paragraph("\n"));
        Paragraph resumo = new Paragraph("Resumo das Transações", fontTitle);
        resumo.setSpacingBefore(10);
        document.add(resumo);

        document.add(new Paragraph("Total Receitas: " + brFormat.format(totalReceitas), fontRow));
        document.add(new Paragraph("Total Despesas: " + brFormat.format(totalGastos), fontRow));
        
        BigDecimal saldo = totalReceitas.subtract(totalGastos);
        Font fontSaldo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, saldo.compareTo(BigDecimal.ZERO) >= 0 ? new Color(16, 185, 129) : Color.RED);
        document.add(new Paragraph("Saldo Final: " + brFormat.format(saldo), fontSaldo));

        document.close();
        return out.toByteArray();
    }

    private String formatarCategoria(Categoria cat) {
        return switch (cat) {
            case RECEITA -> "Receita";
            case GASTO -> "Gasto";
            case GASTO_FIXO -> "Gasto Fixo";
            case ASSINATURA -> "Assinatura";
        };
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
}
