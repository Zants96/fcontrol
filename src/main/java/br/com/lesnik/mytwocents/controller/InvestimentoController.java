package br.com.lesnik.mytwocents.controller;

import br.com.lesnik.mytwocents.dto.AtivoDTO;
import br.com.lesnik.mytwocents.dto.InvestimentoDashboardDTO;
import br.com.lesnik.mytwocents.dto.InvestimentoLancamentoDTO;
import br.com.lesnik.mytwocents.model.AiConfig;
import br.com.lesnik.mytwocents.model.TipoAtivo;
import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import br.com.lesnik.mytwocents.service.CotacaoService;
import br.com.lesnik.mytwocents.service.InvestimentoService;
import br.com.lesnik.mytwocents.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/investimentos")
@RequiredArgsConstructor
public class InvestimentoController {

    private final InvestimentoService service;
    private final CotacaoService cotacaoService;
    private final AiConfigRepository aiConfigRepository;
    private final ExportService exportService;

    /**
     * Exporta os ativos da carteira para CSV.
     */
    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportarCsv() {
        byte[] data = exportService.exportarInvestimentosCsv();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=investimentos.csv");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /**
     * Exporta os ativos da carteira para PDF.
     */
    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarPdf() {
        byte[] data = exportService.exportarInvestimentosPdf();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=investimentos.pdf");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /**
     * Dashboard consolidado de investimentos.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<InvestimentoDashboardDTO> dashboard() {
        return ResponseEntity.ok(service.calcularDashboard());
    }

    /**
     * Lista ativos, opcionalmente filtrados por tipo.
     */
    @GetMapping("/ativos")
    public ResponseEntity<List<AtivoDTO>> listarAtivos(
            @RequestParam(required = false) TipoAtivo tipo) {
        return ResponseEntity.ok(service.listarAtivos(tipo));
    }

    /**
     * Cria um lançamento de investimento (compra, venda ou dividendo).
     */
    @PostMapping("/lancamentos")
    public ResponseEntity<InvestimentoLancamentoDTO> criarLancamento(
            @RequestBody InvestimentoLancamentoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarLancamento(dto));
    }

    /**
     * Atualiza um lançamento de investimento.
     */
    @PutMapping("/lancamentos/{id}")
    public ResponseEntity<InvestimentoLancamentoDTO> atualizarLancamento(
            @PathVariable Long id, @RequestBody InvestimentoLancamentoDTO dto) {
        try {
            return ResponseEntity.ok(service.atualizarLancamento(id, dto));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Lista lançamentos de investimento, opcionalmente filtrados por ativo.
     */
    @GetMapping("/lancamentos")
    public ResponseEntity<List<InvestimentoLancamentoDTO>> listarLancamentos(
            @RequestParam(required = false) Long ativoId) {
        return ResponseEntity.ok(service.listarLancamentos(ativoId));
    }

    /**
     * Histórico completo de proventos com breakdown anual/mensal e por tipo.
     */
    @GetMapping("/proventos/historico")
    public ResponseEntity<Map<String, Object>> proventosHistorico() {
        return ResponseEntity.ok(service.listarProventosHistorico());
    }

    /**
     * Remove um lançamento e recalcula o ativo.
     */
    @DeleteMapping("/lancamentos/{id}")
    public ResponseEntity<Void> excluirLancamento(@PathVariable Long id) {
        try {
            service.excluirLancamento(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Atualiza o preço atual de um ativo manualmente.
     */
    @PutMapping("/ativos/{id}/preco")
    public ResponseEntity<AtivoDTO> atualizarPreco(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal preco = body.get("precoAtual");
        if (preco == null) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok(service.atualizarPreco(id, preco));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Edita dados de um ativo (nome, meta, preço).
     */
    @PutMapping("/ativos/{id}")
    public ResponseEntity<AtivoDTO> atualizarAtivo(
            @PathVariable Long id,
            @RequestBody AtivoDTO dto) {
        try {
            return ResponseEntity.ok(service.atualizarAtivo(id, dto));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Busca cotações atualizadas via BrAPI.
     */
    @PostMapping("/cotacoes/atualizar")
    public ResponseEntity<Map<String, Object>> atualizarCotacoes() {
        String token = aiConfigRepository.findFirstByOrderByIdDesc()
                .map(AiConfig::getBrapiToken)
                .orElse(null);

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Token BrAPI não configurado. Vá em Configurações."));
        }

        int atualizados = cotacaoService.atualizarCotacoes(token);
        return ResponseEntity.ok(Map.of(
                "atualizados", atualizados,
                "message", atualizados + " ativo(s) atualizado(s) com sucesso."
        ));
    }

    /**
     * Salva o token do BrAPI.
     */
    @PostMapping("/brapi/config")
    public ResponseEntity<Map<String, String>> salvarBrapiToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token não pode ser vazio."));
        }

        AiConfig config = aiConfigRepository.findFirstByOrderByIdDesc()
                .orElseGet(() -> AiConfig.builder()
                        .apiKey("")
                        .provider("gemini")
                        .modelo("gemini-1.5-flash")
                        .build());
        config.setBrapiToken(token);
        aiConfigRepository.save(config);

        return ResponseEntity.ok(Map.of("status", "Token BrAPI salvo com sucesso."));
    }

    /**
     * Verifica se o token BrAPI está configurado.
     */
    @GetMapping("/brapi/status")
    public ResponseEntity<Map<String, Object>> brapiStatus() {
        boolean configurado = aiConfigRepository.findFirstByOrderByIdDesc()
                .map(c -> c.getBrapiToken() != null && !c.getBrapiToken().isBlank())
                .orElse(false);
        return ResponseEntity.ok(Map.of("configurado", configurado));
    }
}
