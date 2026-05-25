package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.dto.AiChatDTO;
import br.com.lesnik.mytwocents.dto.AiChatDTO.*;
import br.com.lesnik.mytwocents.dto.DashboardDTO;
import br.com.lesnik.mytwocents.dto.LancamentoDTO;
import br.com.lesnik.mytwocents.model.AiConfig;
import br.com.lesnik.mytwocents.model.Categoria;
import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final AiConfigRepository configRepository;
    private final LancamentoService lancamentoService;
    private final ObjectMapper objectMapper;

    private static final String GEMINI_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    // Subcategorias válidas para referência no prompt
    private static final Map<String, List<String>> SUBCATEGORIAS = Map.of(
            "RECEITA", List.of("13º Salário", "Férias", "Freelancer", "Outras Receitas",
                    "Participação nos Lucros", "Resgate de Investimentos", "Restituição de IR", "Salário", "Vendas"),
            "GASTO", List.of("Água", "Alimentação", "Aluguel", "Cartão de Crédito", "Consultas",
                    "Educação", "Empréstimo", "Investimentos", "Lanches", "Lazer", "Manutenção/Reparos",
                    "Medicamentos", "Outros", "Pets", "Presentes / Doações", "Prestações",
                    "Restaurante", "Saúde & Beleza", "Taxas/Impostos", "Transporte", "Vestuário", "Viagens"),
            "GASTO_FIXO", List.of("Água", "Aluguel", "Condomínio", "Energia/Luz", "Impostos",
                    "Internet", "Investimentos", "Outros", "Prestação", "Seguro", "Seguro Residencial", "Telefonia"),
            "ASSINATURA", List.of("Educação/Cursos", "Jogos/Consoles", "Leitura/Notícias", "Outros",
                    "Serviços de Assinatura", "Serviços Digitais/Cloud", "Streaming de Áudio", "Streaming de Vídeo")
    );

    // ─── CONFIGURAÇÃO ────────────────────────────────────────────────────────

    public StatusResponse getStatus() {
        return configRepository.findFirstByOrderByIdDesc()
                .map(config -> StatusResponse.builder()
                        .configured(true)
                        .provider(config.getProvider())
                        .apiUrl(config.getApiUrl())
                        .modelo(config.getModelo())
                        .build())
                .orElse(StatusResponse.builder().configured(false).build());
    }

    public void salvarConfig(String apiKey, String modelo) {
        salvarConfig(apiKey, modelo, "gemini", null);
    }

    public void salvarConfig(String apiKey, String modelo, String provider, String apiUrl) {
        AiConfig config = configRepository.findFirstByOrderByIdDesc()
                .orElse(new AiConfig());
        config.setApiKey(apiKey);
        config.setProvider(provider);
        config.setModelo(modelo);
        config.setApiUrl(apiUrl);
        configRepository.save(config);
        log.info("API Config ({}) de IA salva/atualizada com sucesso.", modelo);
    }

    // ─── CHAT LIVRE ──────────────────────────────────────────────────────────

    public ChatResponse chat(String message, List<AiChatDTO.ChatMessage> historico, int ano) {
        try {
            String contexto = montarContextoFinanceiro(ano);
            
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Você é o assistente financeiro pessoal do aplicativo MyTwoCents.\n");
            promptBuilder.append("Analise os dados financeiros do usuário e responda a pergunta dele.\n");
            promptBuilder.append("Seja direto, prático, objetivo e use valores em R$ (Real Brasileiro).\n");
            promptBuilder.append("Use formatação Markdown para melhor legibilidade (negrito, listas, etc.).\n");
            promptBuilder.append("Responda sempre em português brasileiro.\n");
            promptBuilder.append("IMPORTANTE: Seja conciso e direto ao ponto. Evite delongas desnecessárias, parágrafos repetitivos ou listagens excessivamente detalhadas para garantir que toda a análise caiba perfeitamente na resposta sem ser cortada.\n\n");
            
            promptBuilder.append(contexto).append("\n");
            
            if (historico != null && !historico.isEmpty()) {
                promptBuilder.append("HISTÓRICO DA CONVERSA ANTERIOR:\n");
                for (AiChatDTO.ChatMessage msg : historico) {
                    String autor = "user".equalsIgnoreCase(msg.getRole()) ? "Usuário" : "Assistente (Você)";
                    promptBuilder.append(autor).append(": ").append(msg.getContent()).append("\n");
                }
                promptBuilder.append("\n");
            }
            
            promptBuilder.append("PERGUNTA ATUAL DO USUÁRIO:\n");
            promptBuilder.append(message).append("\n");

            String resposta = chamarGemini(promptBuilder.toString());
            return ChatResponse.builder().response(resposta).build();
        } catch (Exception e) {
            log.error("Erro no chat da IA: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .error("Erro ao consultar a IA: " + e.getMessage())
                    .build();
        }
    }

    // ─── PARSER DE DOCUMENTOS ────────────────────────────────────────────────

    public ParseResponse parsearDocumento(String texto, int mes, int ano) {
        try {
            String subcategoriasJson = SUBCATEGORIAS.entrySet().stream()
                    .map(e -> "- " + e.getKey() + ": " + String.join(", ", e.getValue()))
                    .collect(Collectors.joining("\n"));

            String prompt = """
                    Você é um parser financeiro especializado. Analise o texto abaixo e extraia TODAS as transações financeiras.
                    
                    CATEGORIAS VÁLIDAS (use exatamente estes nomes):
                    - RECEITA: entrada de dinheiro (salário, freelancer, vendas, etc.)
                    - GASTO: gasto variável do dia-a-dia (alimentação, transporte, compras, etc.)
                    - GASTO_FIXO: gasto fixo mensal (aluguel, energia, internet, condomínio, etc.)
                    - ASSINATURA: serviços recorrentes (Netflix, Spotify, Amazon Prime, Disney+, etc.)
                    
                    SUBCATEGORIAS POR CATEGORIA (use exatamente um destes nomes):
                    %s
                    
                    REGRAS:
                    1. Classifique cada item na categoria MAIS adequada
                    2. Para streaming/serviços digitais recorrentes (Netflix, Spotify, etc), use ASSINATURA
                    3. Para contas mensais fixas (luz, água, internet), use GASTO_FIXO
                    4. Para compras pontuais (iFood, Uber, farmácia, restaurante), use GASTO
                    5. Para entradas de dinheiro, use RECEITA
                    6. Extraia o dia se disponível no texto
                    7. Se o mês/ano estiver no texto, use-o. Caso contrário, use mes=%d e ano=%d
                    8. Use a subcategoria mais adequada da lista acima. Se nenhuma servir, use "Outros"
                    
                    Retorne APENAS um JSON válido, sem markdown, sem explicação, no formato:
                    {
                      "items": [
                        {
                          "descricao": "Nome do item como aparece no documento",
                          "categoria": "GASTO",
                          "subcategoria": "Alimentação",
                          "valor": 39.90,
                          "dia": 14
                        }
                      ],
                      "mes": %d,
                      "ano": %d,
                      "resumo": "Breve resumo do que foi encontrado"
                    }
                    
                    TEXTO PARA ANALISAR:
                    %s
                    """.formatted(subcategoriasJson, mes, ano, mes, ano, texto);

            String resposta = chamarGemini(prompt, "application/json");

            // Limpa a resposta (remove possíveis markdown code blocks)
            resposta = resposta.trim();
            if (resposta.startsWith("```json")) {
                resposta = resposta.substring(7);
            } else if (resposta.startsWith("```")) {
                resposta = resposta.substring(3);
            }
            if (resposta.endsWith("```")) {
                resposta = resposta.substring(0, resposta.length() - 3);
            }
            resposta = resposta.trim();

            JsonNode root = objectMapper.readTree(resposta);

            List<ParsedItem> items = new ArrayList<>();
            JsonNode itemsNode = root.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    items.add(ParsedItem.builder()
                            .descricao(item.has("descricao") ? item.get("descricao").asText() : "")
                            .categoria(item.has("categoria") ? item.get("categoria").asText() : "GASTO")
                            .subcategoria(item.has("subcategoria") ? item.get("subcategoria").asText() : "Outros")
                            .valor(item.has("valor") ? new BigDecimal(item.get("valor").asText()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                            .dia(item.has("dia") && !item.get("dia").isNull() ? item.get("dia").asInt() : null)
                            .build());
                }
            }

            String resumo = root.has("resumo") ? root.get("resumo").asText() : "Documento processado.";

            return ParseResponse.builder()
                    .items(items)
                    .resumo(resumo)
                    .build();

        } catch (Exception e) {
            log.error("Erro ao parsear documento: {}", e.getMessage(), e);
            return ParseResponse.builder()
                    .error("Erro ao processar documento: " + e.getMessage())
                    .items(Collections.emptyList())
                    .build();
        }
    }

    // ─── INSIGHTS AUTOMÁTICOS ────────────────────────────────────────────────

    public InsightResponse gerarInsights(int ano) {
        return gerarInsights(ano, null, null);
    }

    public InsightResponse gerarInsights(int ano, Integer mes, String tipo) {
        if (mes == null || mes <= 0 || tipo == null || tipo.isBlank()) {
            try {
            String contexto = montarContextoFinanceiro(ano);
            String prompt = """
                    Você é um consultor financeiro pessoal analisando os dados do usuário.
                    Gere de 3 a 5 insights PRÁTICOS e ACIONÁVEIS baseados nos dados abaixo.
                    
                    %s
                    
                    REGRAS:
                    1. Cada insight deve ser curto (máximo 2 frases)
                    2. Use valores em R$ quando possível
                    3. Seja específico (mencione categorias e valores reais)
                    4. Inclua pelo menos 1 insight positivo se houver algo bom nos dados
                    5. Foque no mês atual e tendências recentes
                    
                    TIPOS DE INSIGHT:
                    - ALERTA: algo preocupante que precisa de atenção (gastos altos, saldo negativo)
                    - TENDENCIA: padrões de aumento ou diminuição
                    - DICA: sugestão prática de economia
                    - META: projeção ou objetivo sugerido
                    - POSITIVO: elogio ou reconhecimento de boa prática
                    
                    Retorne APENAS um JSON válido, sem markdown, sem explicação:
                    {
                      "insights": [
                        {
                          "tipo": "ALERTA",
                          "mensagem": "Seus gastos com Alimentação...",
                          "icone": "🔴"
                        }
                      ]
                    }
                    """.formatted(contexto);

            String resposta = chamarGemini(prompt, "application/json");

            // Limpa markdown
            resposta = resposta.trim();
            if (resposta.startsWith("```json")) resposta = resposta.substring(7);
            else if (resposta.startsWith("```")) resposta = resposta.substring(3);
            if (resposta.endsWith("```")) resposta = resposta.substring(0, resposta.length() - 3);
            resposta = resposta.trim();

            JsonNode root = objectMapper.readTree(resposta);
            List<Insight> insights = new ArrayList<>();
            JsonNode insightsNode = root.get("insights");
            if (insightsNode != null && insightsNode.isArray()) {
                for (JsonNode node : insightsNode) {
                    insights.add(Insight.builder()
                            .tipo(node.has("tipo") ? node.get("tipo").asText() : "DICA")
                            .mensagem(node.has("mensagem") ? node.get("mensagem").asText() : "")
                            .icone(node.has("icone") ? node.get("icone").asText() : "💡")
                            .build());
                }
            }

            return InsightResponse.builder().insights(insights).build();

        } catch (Exception e) {
            log.error("Erro ao gerar insights: {}", e.getMessage(), e);
            return InsightResponse.builder()
                    .error("Erro ao gerar insights: " + e.getMessage())
                    .insights(Collections.emptyList())
                    .build();
        }
        }

        try {
            // Mapeia o tipo para a Categoria
            Categoria categoriaAlvo = null;
            String tipoLabel = tipo.trim().toLowerCase();
            if (tipoLabel.contains("receita")) {
                categoriaAlvo = Categoria.RECEITA;
            } else if (tipoLabel.contains("fixo")) {
                categoriaAlvo = Categoria.GASTO_FIXO;
            } else if (tipoLabel.contains("assinatura")) {
                categoriaAlvo = Categoria.ASSINATURA;
            } else {
                categoriaAlvo = Categoria.GASTO; // Gasto variável
            }

            final Categoria finalCat = categoriaAlvo;
            List<LancamentoDTO> lancamentos = lancamentoService.listarPorAno(ano).stream()
                    .filter(l -> l.getMes() == mes && l.getCategoria() == finalCat)
                    .toList();

            String[] mesesNomes = {"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};
            String mesNome = (mes >= 1 && mes <= 12) ? mesesNomes[mes - 1] : String.valueOf(mes);
            String labelCategoria = finalCat == Categoria.RECEITA ? "Receitas" :
                                    finalCat == Categoria.GASTO_FIXO ? "Gastos Fixos" :
                                    finalCat == Categoria.ASSINATURA ? "Assinaturas" : "Gastos Variáveis";

            StringBuilder ctx = new StringBuilder();
            ctx.append("ANÁLISE DE ").append(labelCategoria.toUpperCase())
               .append(" — ").append(mesNome.toUpperCase()).append(" DE ").append(ano).append("\n\n");

            if (lancamentos.isEmpty()) {
                return InsightResponse.builder()
                        .insights(Collections.emptyList())
                        .build();
            }
            BigDecimal total = lancamentos.stream()
                    .map(LancamentoDTO::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ctx.append("Total na Categoria: R$ ").append(formatarValor(total)).append("\n");
            ctx.append("Quantidade de Lançamentos: ").append(lancamentos.size()).append("\n\n");
            ctx.append("LISTA DE LANÇAMENTOS DO MÊS:\n");

            // Agrupa e lista por subcategoria de forma resumida
            Map<String, BigDecimal> porSubcat = lancamentos.stream()
                    .collect(Collectors.groupingBy(
                            LancamentoDTO::getSubcategoria,
                            Collectors.reducing(BigDecimal.ZERO, LancamentoDTO::getValor, BigDecimal::add)
                    ));

            porSubcat.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .forEach(e -> ctx.append("- ").append(e.getKey())
                            .append(": R$ ").append(formatarValor(e.getValue())).append("\n"));

            ctx.append("\nDETALHE DOS LANÇAMENTOS INDIVIDUAIS (Maiores valores primeiro):\n");
            lancamentos.stream()
                    .sorted((a, b) -> b.getValor().compareTo(a.getValor()))
                    .limit(20)
                    .forEach(l -> ctx.append("- Dia ").append(l.getDia() != null ? l.getDia() : "-")
                            .append(": ").append(l.getDescricao() != null && !l.getDescricao().isBlank() ? l.getDescricao() : l.getSubcategoria())
                            .append(" (").append(l.getSubcategoria()).append(") — R$ ")
                            .append(formatarValor(l.getValor())).append("\n"));

            String prompt = """
                    Você é um consultor financeiro pessoal analisando especificamente a categoria %s do usuário para o mês de %s de %s.
                    Gere de 2 a 3 insights PRÁTICOS, ACIONÁVEIS, TENDÊNCIAS ou ELOGIOS baseados EXCLUSIVAMENTE nos dados abaixo.
                    
                    %s
                    
                    REGRAS:
                    1. Cada insight deve ser curto (máximo 2 frases)
                    2. Use valores em R$ reais quando possível
                    3. Seja específico (mencione os valores reais e lançamentos que estão nos dados)
                    4. Se não houver lançamentos, sugira metas ou dê uma dica geral de boas práticas para essa categoria
                    
                    TIPOS DE INSIGHT:
                    - ALERTA: gastos excessivos ou desnecessários identificados
                    - TENDENCIA: evolução ou padrão observado
                    - DICA: sugestão prática de economia focada nessa categoria
                    - META: projeção realista para o próximo mês
                    - POSITIVO: reconhecimento caso os gastos estejam controlados ou haja economia
                    
                    Retorne APENAS um JSON válido, sem markdown, sem explicação:
                    {
                      "insights": [
                        {
                          "tipo": "ALERTA",
                          "mensagem": "Seus gastos com...",
                          "icone": "🔴"
                        }
                      ]
                    }
                    """.formatted(labelCategoria, mesNome, ano, ctx.toString());

            String resposta = chamarGemini(prompt, "application/json");

            // Limpa markdown
            resposta = resposta.trim();
            if (resposta.startsWith("```json")) resposta = resposta.substring(7);
            else if (resposta.startsWith("```")) resposta = resposta.substring(3);
            if (resposta.endsWith("```")) resposta = resposta.substring(0, resposta.length() - 3);
            resposta = resposta.trim();

            JsonNode root = objectMapper.readTree(resposta);
            List<Insight> insights = new ArrayList<>();
            JsonNode insightsNode = root.get("insights");
            if (insightsNode != null && insightsNode.isArray()) {
                for (JsonNode node : insightsNode) {
                    insights.add(Insight.builder()
                            .tipo(node.has("tipo") ? node.get("tipo").asText() : "DICA")
                            .mensagem(node.has("mensagem") ? node.get("mensagem").asText() : "")
                            .icone(node.has("icone") ? node.get("icone").asText() : "💡")
                            .build());
                }
            }

            return InsightResponse.builder().insights(insights).build();

        } catch (Exception e) {
            log.error("Erro ao gerar insights mensais por categoria: {}", e.getMessage(), e);
            return InsightResponse.builder()
                    .error("Erro ao gerar insights: " + e.getMessage())
                    .insights(Collections.emptyList())
                    .build();
        }
    }

    // ─── MÉTODOS INTERNOS ────────────────────────────────────────────────────

    /**
     * Monta o contexto financeiro do usuário para envio ao LLM.
     */
    private String montarContextoFinanceiro(int ano) {
        DashboardDTO dashboard = lancamentoService.calcularDashboard(ano);
        List<LancamentoDTO> lancamentos = lancamentoService.listarPorAno(ano);
        int mesAtual = LocalDate.now().getMonthValue();

        StringBuilder ctx = new StringBuilder();
        ctx.append("DADOS FINANCEIROS DO USUÁRIO — ANO ").append(ano).append("\n\n");

        // Totais anuais
        ctx.append("RESUMO ANUAL:\n");
        ctx.append("- Receitas anuais: R$ ").append(formatarValor(dashboard.getTotalReceitas())).append("\n");
        ctx.append("- Gastos anuais (fixos + variáveis): R$ ").append(formatarValor(dashboard.getTotalGastos())).append("\n");
        ctx.append("- Assinaturas anuais: R$ ").append(formatarValor(dashboard.getTotalAssinaturas())).append("\n");
        ctx.append("- Saldo anual: R$ ").append(formatarValor(dashboard.getSaldoAnual())).append("\n\n");

        // Dados mensais
        String[] meses = {"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};

        ctx.append("RECEITAS/GASTOS/SALDO POR MÊS:\n");
        for (int i = 0; i < 12; i++) {
            BigDecimal receita = dashboard.getReceitasPorMes().get(i);
            BigDecimal gasto = dashboard.getGastosPorMes().get(i);
            BigDecimal assinatura = dashboard.getAssinaturasPorMes().get(i);
            BigDecimal saldo = dashboard.getSaldoPorMes().get(i);

            // Só mostra meses com dados
            if (receita.compareTo(BigDecimal.ZERO) > 0 || gasto.compareTo(BigDecimal.ZERO) > 0) {
                ctx.append("- ").append(meses[i]).append(": ");
                ctx.append("Receita R$").append(formatarValor(receita));
                ctx.append(" | Gastos R$").append(formatarValor(gasto));
                ctx.append(" | Assinaturas R$").append(formatarValor(assinatura));
                ctx.append(" | Saldo R$").append(formatarValor(saldo));
                if (i + 1 == mesAtual) ctx.append(" ← MÊS ATUAL");
                ctx.append("\n");
            }
        }

        // Detalhamento de gastos mensais por subcategoria
        ctx.append("\nDETALHAMENTO DE GASTOS POR SUBCATEGORIA EM CADA MÊS:\n");
        for (int i = 0; i < 12; i++) {
            final int mesIdx = i + 1;
            List<LancamentoDTO> gastosDoMes = lancamentos.stream()
                    .filter(l -> l.getMes() == mesIdx && l.getCategoria() != Categoria.RECEITA)
                    .toList();
            
            if (!gastosDoMes.isEmpty()) {
                ctx.append("- ").append(meses[i]).append(":\n");
                
                // Agrupa por subcategoria somando os valores
                Map<String, BigDecimal> subcatValores = gastosDoMes.stream()
                        .collect(Collectors.groupingBy(
                                LancamentoDTO::getSubcategoria,
                                Collectors.reducing(BigDecimal.ZERO, LancamentoDTO::getValor, BigDecimal::add)
                        ));
                
                // Mostra ordenado decrescente
                subcatValores.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                        .forEach(e -> ctx.append("  * ").append(e.getKey())
                                .append(": R$ ").append(formatarValor(e.getValue())).append("\n"));
            }
        }

        // Top gastos por subcategoria anuais
        if (dashboard.getGastosPorSubcategoria() != null && !dashboard.getGastosPorSubcategoria().isEmpty()) {
            ctx.append("\nTOP GASTOS POR SUBCATEGORIA (Ano inteiro):\n");
            dashboard.getGastosPorSubcategoria().entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> ctx.append("- ").append(e.getKey())
                            .append(": R$ ").append(formatarValor(e.getValue())).append("\n"));
        }

        // Data atual para contexto temporal
        ctx.append("\nDATA ATUAL: ").append(LocalDate.now()).append("\n");

        return ctx.toString();
    }

    private String chamarGemini(String prompt) {
        return chamarGemini(prompt, null);
    }

    private String chamarGemini(String prompt, String responseMimeType) {
        AiConfig config = configRepository.findFirstByOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("API Key de IA não configurada. Vá em Configurações para adicionar."));

        String provider = config.getProvider();
        String apiUrl = config.getApiUrl();
        
        boolean isGemini = (provider == null || "gemini".equalsIgnoreCase(provider)) && (apiUrl == null || apiUrl.isBlank());
        
        if (!isGemini) {
            // Chamada compatível com OpenAI (DeepSeek, Groq, Grok/xAI, OpenAI)
            String targetUrl = apiUrl;
            if (targetUrl == null || targetUrl.isBlank()) {
                if ("groq".equalsIgnoreCase(provider)) {
                    targetUrl = "https://api.groq.com/openai/v1/chat/completions";
                } else if ("deepseek".equalsIgnoreCase(provider)) {
                    targetUrl = "https://api.deepseek.com/v1/chat/completions";
                } else if ("grok".equalsIgnoreCase(provider) || "xai".equalsIgnoreCase(provider)) {
                    targetUrl = "https://api.x.ai/v1/chat/completions";
                } else {
                    targetUrl = "https://api.openai.com/v1/chat/completions"; // Fallback para OpenAI
                }
            } else {
                if (!targetUrl.endsWith("/chat/completions")) {
                    if (targetUrl.endsWith("/")) {
                        targetUrl += "chat/completions";
                    } else {
                        targetUrl += "/chat/completions";
                    }
                }
            }
            
            // Corpo da requisição compatível com OpenAI
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModelo());
            body.put("temperature", 0.5);
            body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));
            
            if (responseMimeType != null && "application/json".equalsIgnoreCase(responseMimeType)) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<JsonNode> response = restTemplate.postForEntity(targetUrl, request, JsonNode.class);
                if (response.getBody() == null) {
                    throw new RuntimeException("Resposta vazia da API de IA.");
                }
                
                // Extração padrão OpenAI: choices[0].message.content
                JsonNode choices = response.getBody().get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).get("message");
                    if (messageNode != null && messageNode.has("content")) {
                        return messageNode.get("content").asText();
                    }
                }
                throw new RuntimeException("Formato de resposta inesperado da API de IA.");
            } catch (Exception e) {
                log.error("Erro ao chamar API customizada ({}): {}", targetUrl, e.getMessage());
                throw new RuntimeException("Erro na chamada da API customizada: " + e.getMessage(), e);
            }
        }

        String url = String.format(GEMINI_URL_TEMPLATE, config.getModelo(), config.getApiKey());

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.5);
        generationConfig.put("maxOutputTokens", 4096);
        if (responseMimeType != null) {
            generationConfig.put("responseMimeType", responseMimeType);
        }

        // Monta o body no formato esperado pelo Gemini
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", generationConfig
        );

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);

            if (response.getBody() == null) {
                throw new RuntimeException("Resposta vazia do Gemini.");
            }

            // Extrai o texto da resposta
            JsonNode candidates = response.getBody().get("candidates");
            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                JsonNode candidate = candidates.get(0);
                if (candidate.has("finishReason")) {
                    log.info("Gemini finishReason: {}", candidate.get("finishReason").asText());
                }
                JsonNode content = candidate.get("content");
                if (content != null) {
                    JsonNode parts = content.get("parts");
                    if (parts != null && parts.isArray() && parts.size() > 0) {
                        StringBuilder fullResponse = new StringBuilder();
                        for (JsonNode part : parts) {
                            if (part.has("text")) {
                                fullResponse.append(part.get("text").asText());
                            }
                        }
                        String respStr = fullResponse.toString();
                        log.info("Gemini response length: {} characters", respStr.length());
                        return respStr;
                    }
                }
            }

            throw new RuntimeException("Formato de resposta inesperado do Gemini.");

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String msg = e.getResponseBodyAsString();
            log.error("Erro HTTP do Gemini: {} - {}", e.getStatusCode(), msg);
            
            try {
                JsonNode root = objectMapper.readTree(msg);
                if (root.has("error") && root.get("error").has("message")) {
                    String apiErrorMessage = root.get("error").get("message").asText();
                    if (apiErrorMessage.contains("monthly spending cap")) {
                        throw new RuntimeException("A chave de API atingiu o limite de gastos mensal (spending cap) configurado no Google AI Studio. Acesse https://aistudio.google.com/ e configure um limite de gastos adequado.");
                    }
                    throw new RuntimeException(apiErrorMessage);
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ignored) {
                // Usa tratamento padrão se falhar o parse do JSON de erro
            }

            if (e.getStatusCode().value() == 429) {
                throw new RuntimeException("Limite de requisições atingido. Tente novamente em alguns minutos.");
            } else if (e.getStatusCode().value() == 400) {
                throw new RuntimeException("Erro na requisição ao Gemini. Verifique sua API Key.");
            }
            throw new RuntimeException("Erro do Gemini: " + e.getStatusCode());
        }
    }

    private String formatarValor(BigDecimal valor) {
        if (valor == null) return "0,00";
        return valor.setScale(2, RoundingMode.HALF_UP).toString().replace(".", ",");
    }
}
