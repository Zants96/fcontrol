package br.com.lesnik.mytwocents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chave de API do provedor (ex: Google Gemini) */
    @Column(nullable = false, length = 512)
    private String apiKey;

    /** Provedor de IA (gemini, groq, etc.) — futuro suporte multi-provider */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String provider = "gemini";

    /** URL Base customizada para a API (opcional, para Grok, DeepSeek, OpenAI, etc.) */
    @Column(length = 512)
    private String apiUrl;

    /** Modelo a ser utilizado */
    @Column(nullable = false, length = 100)
    @Builder.Default
    private String modelo = "gemini-2.5-flash";

    /** Token do BrAPI.dev para cotações de investimentos */
    @Column(length = 512)
    private String brapiToken;

    /** Chave de API do CoinGecko para cotações de criptomoedas */
    @Column(length = 512)
    private String coingeckoKey;

    /** Meta de reserva da caixa de emergência em R$ */
    @Column(name = "emergency_box_target", precision = 15, scale = 2)
    @Builder.Default
    private java.math.BigDecimal emergencyBoxTarget = new java.math.BigDecimal("20000.00");

    /** Renda mensal estimada para cálculo da reserva */
    @Column(name = "monthly_income", precision = 15, scale = 2)
    @Builder.Default
    private java.math.BigDecimal monthlyIncome = new java.math.BigDecimal("5000.00");

    // ─── Metas percentuais por tipo de ativo (devem somar 100%) ───
    @Column(name = "meta_acao", precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal metaAcao = new java.math.BigDecimal("25.00");

    @Column(name = "meta_fii", precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal metaFii = new java.math.BigDecimal("15.00");

    @Column(name = "meta_renda_fixa", precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal metaRendaFixa = new java.math.BigDecimal("20.00");

    @Column(name = "meta_etf", precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal metaEtf = new java.math.BigDecimal("15.00");

    @Column(name = "meta_tesouro", precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal metaTesouro = new java.math.BigDecimal("20.00");

    @Column(name = "meta_cripto", precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal metaCripto = new java.math.BigDecimal("5.00");

    @Column(updatable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
