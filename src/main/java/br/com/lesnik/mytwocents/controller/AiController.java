package br.com.lesnik.mytwocents.controller;

import br.com.lesnik.mytwocents.dto.AiChatDTO.*;
import br.com.lesnik.mytwocents.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * Verifica se a IA está configurada (API key presente).
     * GET /api/ai/status
     */
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status() {
        return ResponseEntity.ok(aiService.getStatus());
    }

    /**
     * Salva ou atualiza a API key.
     * POST /api/ai/config
     */
    @PostMapping("/config")
    public ResponseEntity<StatusResponse> saveConfig(@RequestBody ConfigRequest request) {
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String modelo = request.getModelo() != null && !request.getModelo().isBlank() 
                ? request.getModelo().trim() : "gemini-1.5-flash";
        String provider = request.getProvider() != null && !request.getProvider().isBlank()
                ? request.getProvider().trim() : "gemini";
        String apiUrl = request.getApiUrl() != null && !request.getApiUrl().isBlank()
                ? request.getApiUrl().trim() : null;

        aiService.salvarConfig(request.getApiKey().trim(), modelo, provider, apiUrl);
        return ResponseEntity.ok(aiService.getStatus());
    }

    /**
     * Chat livre com a IA.
     * POST /api/ai/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        int ano = request.getAno() != null ? request.getAno() : LocalDate.now().getYear();
        ChatResponse response = aiService.chat(request.getMessage(), request.getHistorico(), ano);
        return ResponseEntity.ok(response);
    }

    /**
     * Parser de documentos: extrai transações de texto colado.
     * POST /api/ai/parse
     */
    @PostMapping("/parse")
    public ResponseEntity<ParseResponse> parse(@RequestBody ParseRequest request) {
        int mes = request.getMes() != null ? request.getMes() : LocalDate.now().getMonthValue();
        int ano = request.getAno() != null ? request.getAno() : LocalDate.now().getYear();
        ParseResponse response = aiService.parsearDocumento(request.getTexto(), mes, ano);
        return ResponseEntity.ok(response);
    }

    /**
     * Gera insights automáticos baseados nos dados financeiros.
     * GET /api/ai/insights?ano=2026
     */
    @GetMapping("/insights")
    public ResponseEntity<InsightResponse> insights(
            @RequestParam(defaultValue = "0") Integer ano,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) String tipo) {
        if (ano == 0) ano = LocalDate.now().getYear();
        InsightResponse response = aiService.gerarInsights(ano, mes, tipo);
        return ResponseEntity.ok(response);
    }

    /**
     * Gera insights automáticos baseados no portfólio de investimentos.
     * GET /api/ai/insights/investimentos
     */
    @GetMapping("/insights/investimentos")
    public ResponseEntity<InsightResponse> insightsInvestimentos() {
        InsightResponse response = aiService.gerarInsightsInvestimentos();
        return ResponseEntity.ok(response);
    }
}
