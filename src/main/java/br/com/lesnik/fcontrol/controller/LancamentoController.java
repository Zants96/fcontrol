package br.com.lesnik.fcontrol.controller;

import br.com.lesnik.fcontrol.dto.DashboardDTO;
import br.com.lesnik.fcontrol.dto.LancamentoDTO;
import br.com.lesnik.fcontrol.model.Categoria;
import br.com.lesnik.fcontrol.service.ExportService;
import br.com.lesnik.fcontrol.service.LancamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LancamentoController {

    private final LancamentoService service;
    private final ExportService exportService;

    /**
     * Lista lançamentos filtrados por ano e opcionalmente por mês e/ou categoria.
     * GET /api/lancamentos?ano=2025
     * GET /api/lancamentos?ano=2025&mes=4
     * GET /api/lancamentos?ano=2025&categoria=GASTO
     */
    @GetMapping("/lancamentos")
    public ResponseEntity<List<LancamentoDTO>> listar(
            @RequestParam(defaultValue = "0") Integer ano,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Categoria categoria) {

        if (ano == 0) ano = LocalDate.now().getYear();

        List<LancamentoDTO> resultado;
        if (mes != null && categoria != null) {
            resultado = service.listarPorAnoEMes(ano, mes).stream()
                    .filter(l -> l.getCategoria() == categoria).toList();
        } else if (mes != null) {
            resultado = service.listarPorAnoEMes(ano, mes);
        } else if (categoria != null) {
            resultado = service.listarPorAnoECategoria(ano, categoria);
        } else {
            resultado = service.listarPorAno(ano);
        }

        return ResponseEntity.ok(resultado);
    }

    /**
     * Cria um novo lançamento.
     * POST /api/lancamentos
     */
    @PostMapping("/lancamentos")
    public ResponseEntity<LancamentoDTO> criar(@RequestBody LancamentoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    /**
     * Atualiza um lançamento existente.
     * PUT /api/lancamentos/{id}
     */
    @PutMapping("/lancamentos/{id}")
    public ResponseEntity<LancamentoDTO> atualizar(@PathVariable Long id, @RequestBody LancamentoDTO dto) {
        try {
            return ResponseEntity.ok(service.atualizar(id, dto));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Remove um lançamento.
     * DELETE /api/lancamentos/{id}?excluirProximos=true
     */
    @DeleteMapping("/lancamentos/{id}")
    public ResponseEntity<Void> excluir(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean excluirProximos) {
        try {
            service.excluir(id, excluirProximos);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Retorna dados agregados para o dashboard.
     * GET /api/dashboard?ano=2025
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDTO> dashboard(
            @RequestParam(defaultValue = "0") Integer ano) {
        if (ano == 0) ano = LocalDate.now().getYear();
        return ResponseEntity.ok(service.calcularDashboard(ano));
    }

    @GetMapping("/lancamentos/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam Integer ano,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) String view) {
        
        Categoria categoria = resolverCategoria(view);
        byte[] data = exportService.exportarCsv(ano, mes, categoria);
        String filename = gerarNomeArquivo("fcontrol", ano, mes, view, "csv");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    @GetMapping("/lancamentos/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam Integer ano,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) String view) {
        
        Categoria categoria = resolverCategoria(view);
        byte[] data = exportService.exportarPdf(ano, mes, categoria);
        String filename = gerarNomeArquivo("fcontrol", ano, mes, view, "pdf");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    private Categoria resolverCategoria(String view) {
        if (view == null) return null;
        return switch (view) {
            case "receitas" -> Categoria.RECEITA;
            case "gastos" -> Categoria.GASTO;
            case "gastos-fixos" -> Categoria.GASTO_FIXO;
            case "assinaturas" -> Categoria.ASSINATURA;
            default -> null;
        };
    }

    private String gerarNomeArquivo(String prefix, Integer ano, Integer mes, String view, String ext) {
        StringBuilder name = new StringBuilder("FControl");
        name.append(" - ").append(ano);
        
        if (mes != null && mes > 0 && mes <= 12) {
            name.append(" - ").append(ExportService.MESES[mes - 1]);
        }
        
        if (view != null && !view.equalsIgnoreCase("dashboard")) {
            String aba = switch (view) {
                case "receitas" -> "Receitas";
                case "gastos" -> "Gastos";
                case "gastos-fixos" -> "Gastos Fixos";
                case "assinaturas" -> "Assinaturas";
                default -> view;
            };
            name.append(" - ").append(aba);
        } else {
            name.append(" - Geral");
        }
        
        return name.append(".").append(ext).toString();
    }
}
