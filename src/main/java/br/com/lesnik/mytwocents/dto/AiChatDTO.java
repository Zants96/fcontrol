package br.com.lesnik.mytwocents.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs para comunicação com o módulo de IA.
 */
public class AiChatDTO {

    // ─── Chat ────────────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChatMessage {
        private String role;
        private String content;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChatRequest {
        private String message;
        private Integer ano;
        private List<ChatMessage> historico;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChatResponse {
        private String response;
        private Integer tokensUsed;
        private String error;
    }

    // ─── Parser de Documentos ────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParseRequest {
        private String texto;
        private Integer mes;
        private Integer ano;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParseResponse {
        private List<ParsedItem> items;
        private List<ParsedInvestimentoItem> investimentos;
        private String resumo;
        private String error;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParsedItem {
        private String descricao;
        private String categoria;
        private String subcategoria;
        private BigDecimal valor;
        private Integer dia;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParsedInvestimentoItem {
        private String ticker;
        private String tipoAtivo;      // ACAO, FII, RENDA_FIXA, ETF, TESOURO_DIRETO, CRIPTO
        private String tipoOperacao;   // COMPRA, VENDA, DIVIDENDO
        private BigDecimal quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal custos;
        private BigDecimal valorTotal;
        private BigDecimal valorLiquido;
        private Integer dia;
        private String dataVencimento; // DD/MM/YYYY
        private String indexador;
        private BigDecimal taxa;
        private String data;           // YYYY-MM-DD
    }

    // ─── Insights ────────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InsightResponse {
        private List<Insight> insights;
        private String error;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Insight {
        /** Tipos: ALERTA, TENDENCIA, DICA, META, POSITIVO */
        private String tipo;
        private String mensagem;
        private String icone;
    }

    // ─── Configuração ────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConfigRequest {
        private String apiKey;
        private String modelo;
        private String provider;
        private String apiUrl;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatusResponse {
        private boolean configured;
        private String provider;
        private String apiUrl;
        private String modelo;
    }
}
