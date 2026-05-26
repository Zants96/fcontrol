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
