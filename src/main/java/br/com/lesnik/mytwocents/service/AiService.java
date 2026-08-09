package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.dto.AiChatDTO;
import br.com.lesnik.mytwocents.dto.AiChatDTO.*;
import br.com.lesnik.mytwocents.dto.DashboardDTO;
import br.com.lesnik.mytwocents.dto.LancamentoDTO;
import br.com.lesnik.mytwocents.dto.InvestimentoDashboardDTO;
import br.com.lesnik.mytwocents.dto.AtivoDTO;
import br.com.lesnik.mytwocents.model.AiConfig;
import br.com.lesnik.mytwocents.model.Categoria;
import br.com.lesnik.mytwocents.model.InvestimentoLancamento;
import br.com.lesnik.mytwocents.model.Lancamento;
import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import br.com.lesnik.mytwocents.repository.InvestimentoLancamentoRepository;
import br.com.lesnik.mytwocents.repository.LancamentoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final InvestimentoService investimentoService;
    private final LancamentoRepository lancamentoRepository;
    private final InvestimentoLancamentoRepository investimentoLancamentoRepository;
    private final ObjectMapper objectMapper;

    private static final String GEMINI_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String FILOSOFIA_INVESTIMENTOS = """
            DIRETRIZES E FILOSOFIA DE INVESTIMENTOS DA CARTEIRA:
            1. Missão & Objetivo: Acúmulo de patrimônio considerável para gerar renda passiva mensal (preservando poder de compra). Construção sistemática de um exército de ativos reais.
            2. Pilares de Investimento:
               - Valor (Value Investing): Apenas ativos geradores de lucro real, com dívida controlada e governança sólida. Rejeite promessas ou empresas deficitárias.
               - Crescimento (Compounding): Foco em empresas que reinvestem para crescer e no Yield on Cost.
               - Proteção (Blindagem): Reserva pós-fixada, dolarização via ETFs e ouro. Para patrimônios de 7 dígitos+, o foco passa a ser preservação do poder de compra.
            3. Filtros de Qualidade e Critérios de Análise:
               - ROIC > 10%%, Dívida Líquida / EBITDA < 3.0x, Tag Along sempre de 100%%.
               - Governança: Novo Mercado ou histórico impecável de respeito ao minoritário.
               - Margem de Segurança: Comprar apenas abaixo do Preço-Teto (fórmulas de Graham/Bazin).
               - Zero Especulação: Proibido day trading, derivativos, meme coins ou qualquer ativo sem fundamentos.
            4. Arquitetura Alocação Meta:
               - Segurança (20%%): Renda Fixa pós-fixada e Tesouro Direto/Selic (liquidez).
               - Renda (25%%): FIIs (Papel/Tijolo) e Ações de Transmissoras de Energia.
               - Crescimento (30%%): Ações líderes em setores perenes.
               - Global/Proteção (20%%): Dolarização via ETFs e reserva de valor.
               - Criptomoedas (5%%): Cripto consolidadas (ex: BTC/ETH/XRP/SOL/ADA), proibido meme coins.
            5. Conduta e Execução de Carteira:
               - Aporte mensal inegociável e reinvestimento imediato de 100%% dos proventos no ativo mais abaixo da meta percentual.
               - Preço Médio Tático: Aportar em ativos de alta qualidade durante quedas (comprar barato com margem).
               - Diretriz de Deterioração: Se a governança ou tese estrutural de um ativo for quebrada, sugerir colocá-lo em "Quarentena" (manter sem novos aportes) ou substituição por ativo superior da mesma classe.
               - Consistência Histórica: Consultar sempre o histórico de preferências do usuário (Ledger de Correções) para evitar contradições.
            """;

    // Constant do Prompt Base Centralizado e Modularizado
    private static final String SYSTEM_PROMPT_BASE = """
            Você é um consultor financeiro sênior com 30 anos de mercado. Sua atuação é rigorosamente dividida.

            PASSO 1: Analise o contexto da aba e aplique o filtro de especialidade (Doméstica ou Carteira).
            PASSO 2: Identifique dados positivos e desperdícios.
            PASSO 3: Gere insights priorizando o impacto financeiro.

            1. ESPECIALISTA EM ORÇAMENTO (Assessoria Doméstica): Focado em eficiência de fluxo de caixa, cortes de despesas fixas excessivas, caça a desperdícios e equilíbrio de vida.
               - Caso identifique que o usuário tem muito pouca ou nenhuma despesa de lazer/bem-estar nos dados, sugira de forma esporádica e sutil que utilize uma parte da economia gerada para cuidar de si mesmo, mantendo a jornada saudável de longo prazo.
            2. ESPECIALISTA EM INVESTIMENTOS (Assessoria de Carteira): Focado em alocação estratégica, análise fundamentalista sólida, compounding e risco sistêmico.

            DIRETRIZES DE COMPORTAMENTO POR CONTEXTO:
            - Se a aba/contexto for a página principal/geral ('HOME' ou 'Dashboard Anual - Finanças Gerais Pessoais'): Limite-se estritamente à Assessoria Doméstica. Você deve retornar análises APENAS sobre a gestão financeira geral, gastos, receitas, fluxo de caixa e desperdícios. É expressamente proibido citar tickers de ações, FIIs, renda fixa ou realizar qualquer análise sobre a carteira de investimentos.
            - Se a aba/contexto for de controle de gastos/receitas/assinaturas/cartões (Finança Pessoal Caseira): Limite-se estritamente à Assessoria Doméstica. Dê dicas de otimização de gastos, eficiência, caça a desperdícios e planos. NÃO cite tickers de ações/FIIs ou juros compostos complexos.
            - Se a aba/contexto for de investimentos/renda variável ou 'Carteira de Investimentos': Limite-se estritamente à Assessoria de Carteira. Faça análises profundas, cite tickers reais, ROIC, preço-teto e balanceamento. As análises de investimentos ocorrem APENAS aqui.

            Sua comunicação é assertiva, técnica, pragmática e direta, mas altamente encorajadora e motivadora: reconheça atitudes positivas (constância de aportes, redução de desperdícios), elogie a disciplina e comemore pequenas vitórias.

            FILOSOFIA DE INVESTIMENTOS:
            %s

            DIRETRIZES DE EXECUÇÃO:
            1. PRIORIZAÇÃO: Ordene os insights pelo impacto financeiro (maior primeiro).
            2. ESPECIFICIDADE: Cite os valores reais (R$) e itens presentes nos dados.
            3. QUANTIDADE: Gere rigorosamente no mínimo 6 insights (seis ou mais itens no array de retorno).
            4. FORMATO DE SAÍDA: Retorne APENAS o JSON válido conforme a estrutura abaixo. Nenhuma outra introdução ou texto fora do JSON.

            %s
            """;

    // Estrutura Base do JSON para garantir consistência
    private static final String JSON_STRUCTURE = """
            Retorne APENAS um JSON válido seguindo estritamente este formato. Não use markdown.
            {
              "insights": [
                {
                  "tipo": "ALERTA" | "TENDENCIA" | "DICA" | "META" | "POSITIVO" | "ESTRATEGICO" | "EFICIENCIA",
                  "mensagem": "Texto curto (max 2 frases), focado e de alto valor.",
                  "icone": "🔴" | "📈" | "💡" | "🎯" | "🏆" | "⚖️" | "⚡",
                  "impacto": "ALTO" | "MEDIO" | "BAIXO",
                  "prioridade": 1 (máxima) a 5 (mínima)
                }
              ]
            }
            """;

    // ─── MÉTODO CENTRALIZADO DE INSIGHTS (Refatorado) ────────────────────────

    public InsightResponse gerarInsightsCustom(String contextoAba, String dados, String foco) {
        String base = SYSTEM_PROMPT_BASE.formatted(FILOSOFIA_INVESTIMENTOS, JSON_STRUCTURE);
        String prompt = """
                %s

                CONTEXTO ATUAL DA ANÁLISE: %s
                DADOS DISPONÍVEIS:
                %s

                FOCO COMPLEMENTAR DA ABA: %s
                """.formatted(base, contextoAba, dados, foco);

        try {
            String resposta = chamarGemini(prompt, "application/json");
            resposta = limparMarkdown(resposta);

            JsonNode root = objectMapper.readTree(resposta);
            List<Insight> insights = new ArrayList<>();
            JsonNode insightsNode = root.get("insights");

            if (insightsNode != null && insightsNode.isArray()) {
                for (JsonNode node : insightsNode) {
                    insights.add(Insight.builder()
                            .tipo(node.has("tipo") ? node.get("tipo").asText() : "DICA")
                            .mensagem(node.has("mensagem") ? node.get("mensagem").asText() : "")
                            .icone(node.has("icone") ? node.get("icone").asText() : "💡")
                            .impacto(node.has("impacto") ? node.get("impacto").asText() : "MEDIO")
                            .prioridade(node.has("prioridade") ? node.get("prioridade").asInt() : 3)
                            .build());
                }
            }
            // Ordena pela prioridade (1 máxima, 5 mínima)
            insights.sort(Comparator.comparingInt(i -> i.getPrioridade() != null ? i.getPrioridade() : 5));
            return InsightResponse.builder().insights(insights).build();
        } catch (Exception e) {
            log.error("Erro ao gerar insights via Consultor Elite: {}", e.getMessage());
            return InsightResponse.builder()
                    .error("Erro ao gerar insights: " + e.getMessage())
                    .insights(Collections.emptyList())
                    .build();
        }
    }

    private String limparMarkdown(String resposta) {
        resposta = resposta.trim();
        if (resposta.startsWith("```json"))
            resposta = resposta.substring(7);
        else if (resposta.startsWith("```"))
            resposta = resposta.substring(3);
        if (resposta.endsWith("```"))
            resposta = resposta.substring(0, resposta.length() - 3);
        return resposta.trim();
    }

    // Subcategorias válidas para referência no prompt
    private static final Map<String, List<String>> SUBCATEGORIAS = Map.of(
            "RECEITA", List.of("13º Salário", "Férias", "Freelancer", "Outras Receitas",
                    "Participação nos Lucros", "Proventos", "Restituição de IR", "Salário",
                    "Vendas"),
            "GASTO", List.of("Água", "Alimentação", "Aluguel", "Cartão de Crédito", "Casa & Decoração", "Consultas",
                    "Delivery / Apps", "Educação", "Eletrônicos", "Empréstimo", "Lanches", "Lazer",
                    "Manutenção/Reparos",
                    "Medicamentos", "Outros", "Pets", "Presentes / Doações", "Prestações",
                    "Restaurante", "Saúde & Beleza", "Taxas/Impostos", "Transporte", "Vestuário", "Viagens"),
            "GASTO_FIXO", List.of("Água", "Aluguel", "Condomínio", "Energia/Luz", "Impostos",
                    "Internet", "Outros", "Plano de Saúde", "Plano Odontológico", "Prestação",
                    "Seguro", "Seguro Residencial", "Telefonia"),
            "ASSINATURA", List.of("Educação/Cursos", "Jogos/Consoles", "Leitura/Notícias", "Outros",
                    "Serviços de Assinatura", "Serviços Digitais/Cloud", "Streaming de Áudio", "Streaming de Vídeo"),
            "TRANSFERENCIA", List.of("Investimentos", "Ações", "FIIs", "Renda Fixa", "ETFs", "Tesouro Direto", "Criptomoedas", "Fundos", "Stock", "BDRs", "Reit", "Poupança", "Resgate de Investimentos", "Outras Transferências"));

    // ─── CONFIGURAÇÃO ────────────────────────────────────────────────────────

    public StatusResponse getStatus() {
        return configRepository.findFirstByOrderByIdDesc()
                .map(config -> {
                    String modelo = config.getModelo();
                    if (modelo == null || "gemini-3.1-flash-lite".equalsIgnoreCase(modelo)
                            || "gemini-1.5-flash".equalsIgnoreCase(modelo)) {
                        modelo = "gemini-2.5-flash";
                        config.setModelo(modelo);
                        configRepository.save(config);
                    }
                    return StatusResponse.builder()
                            .configured(true)
                            .provider(config.getProvider())
                            .apiUrl(config.getApiUrl())
                            .modelo(modelo)
                            .build();
                })
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
            // Direcionamento dinâmico de contexto com base na pergunta do usuário
            String msgLower = message.toLowerCase();
            boolean isInvestimentos = msgLower.contains("ações") || msgLower.contains("fii")
                    || msgLower.contains("investir")
                    || msgLower.contains("investimento") || msgLower.contains("carteira de investimentos")
                    || msgLower.contains("portfólio")
                    || msgLower.contains("renda fixa") || msgLower.contains("selic") || msgLower.contains("cdi")
                    || msgLower.contains("tesouro") || msgLower.contains("cripto") || msgLower.contains("btc")
                    || msgLower.contains("eth");

            boolean isOrcamento = msgLower.contains("gastos") || msgLower.contains("despesa")
                    || msgLower.contains("assinatura")
                    || msgLower.contains("mercado") || msgLower.contains("saldo") || msgLower.contains("receita")
                    || msgLower.contains("lazer") || msgLower.contains("economia") || msgLower.contains("poupar");

            String contexto = montarContextoFinanceiro(ano, isInvestimentos);

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("--- PERSONA E OBJETIVOS ---\n");
            promptBuilder.append(
                    "Você é o consultor financeiro sênior do MyTwoCents, com 30 anos de mercado. Sua missão é a gestão holística: eficiência orçamentária (corte de desperdícios) + gestão estratégica de portfólio (Value Investing).\n");
            promptBuilder.append(
                    "Sua voz é técnica e assertiva, mas humanizada e empática: reconheça vitórias, promova a disciplina e defenda o 'Equilíbrio de Vida'. Se o usuário for austero demais, force-o a alocar uma verba de lazer/bem-estar para sustentar a jornada de longo prazo.\n\n");

            promptBuilder.append("--- REGRAS DE EXECUÇÃO ---\n");
            promptBuilder.append(
                    "1. PRIORIZAÇÃO: Identifique e ataque primeiro o maior ralo de dinheiro (desperdício) ou a maior ineficiência na carteira de investimentos (se aplicável).\n");
            promptBuilder.append(
                    "2. ANÁLISE DE ATIVOS: Ao citar consenso de mercado (LSEG/Refinitiv/Bloomberg), use tabelas Markdown. Se não houver consenso sólido, declare a ausência de dados publicamente.\n");
            promptBuilder.append(
                    "3. FILOSOFIA: Siga estritamente " + FILOSOFIA_INVESTIMENTOS + ". Rejeite especulação pura.\n");
            promptBuilder.append(
                    "4. FORMATO: Respostas em português brasileiro. Use Markdown para legibilidade. Seja conciso.\n\n");

            promptBuilder.append("--- LÓGICA DE RACIOCÍNIO ---\n");
            promptBuilder.append(
                    "Antes de responder, verifique: 1) Qual a tendência atual do saldo? 2) O usuário atingiu o aporte meta? 3) Existe algum gasto vampiro? 4) O portfólio está alinhado à filosofia?\n");
            promptBuilder.append(
                    "Responda sempre com a estrutura: [Resumo/Status] -> [Análise/Diagnóstico] -> [Tabela de Ativos, se solicitado] -> [Recomendação/Plano de Ação] -> [Elogio/Motivação Final].\n\n");

            promptBuilder.append("--- ORIENTAÇÃO DE CONTEXTO ---\n");
            if (isInvestimentos && !isOrcamento) {
                promptBuilder.append(
                        "Atenção: A pergunta é sobre INVESTIMENTOS. Mantenha o foco técnico em ativos, indicadores fundamentalistas e nossa filosofia de alocação.\n\n");
            } else if (isOrcamento && !isInvestimentos) {
                promptBuilder.append(
                        "Atenção: A pergunta é sobre ASSESSORIA DOMÉSTICA (Orçamento). Mantenha o foco em eficiência de fluxo de caixa, economia prática e equilíbrio de vida.\n\n");
            } else {
                promptBuilder.append(
                        "Atenção: A pergunta é MISTA ou GERAL. Foque nas finanças pessoais domésticas e orçamento geral. Não realize análises sobre investimentos ou carteira a menos que expressamente provocado sobre investimentos.\n\n");
            }

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

    @Transactional(readOnly = true)
    public ParseResponse parsearDocumento(String texto, int mes, int ano) {
        try {
            String subcategoriasJson = SUBCATEGORIAS.entrySet().stream()
                    .map(e -> "- " + e.getKey() + ": " + String.join(", ", e.getValue()))
                    .collect(Collectors.joining("\n"));

            String prompt = """
                    Você é um parser financeiro e de investimentos altamente qualificado. Analise o texto abaixo e extraia TODAS as transações e posições financeiras.

                    O texto pode ser de dois tipos:
                    1. Transações comuns: faturas de cartão de crédito, boletos, contas ou notas fiscais.
                    2. Lançamentos de investimentos/proventos: extratos de negociação, comprovantes de dividendos, ou listas de posições atuais de custódia (portfólio).

                    PARTE 1: TRANSAÇÕES COMUNS (extrair para a chave "items")
                    - CATEGORIAS: RECEITA, GASTO, GASTO_FIXO, ASSINATURA, TRANSFERENCIA.
                    - SUBCATEGORIAS VÁLIDAS POR CATEGORIA:
                    %s
                    - Regras: Extraia descrição, categoria, subcategoria, valor e dia.

                    PARTE 2: LANÇAMENTOS DE INVESTIMENTO E CUSTÓDIA (extrair para a chave "investimentos")
                    - Se o texto contiver posições de carteira, custódia ou ativos atuais (ex: BBAS3 com quantidade e preço médio/compra), trate cada ativo como uma operação de "COMPRA" com a quantidade e o preço médio listados. Isso permitirá a inicialização da carteira.
                    - Se o texto contiver transações de compra/venda de ativos, extraia a operação correspondente ("COMPRA" ou "VENDA").
                    - Se o texto contiver proventos recebidos (dividendos, JCP, rendimentos de FII), extraia a operação correspondente ("DIVIDENDO").
                    - Mapeie "tipoAtivo" rigorosamente para:
                      * ACAO: ações (ex: BBAS3, PETR4, BBAS3, CXSE3, VULC3, ABCB4, RANI3)
                      * FII: fundos imobiliários (ex: MXRF11, HGLG11, IFRI11, XPML11, RZTR11)
                      * RENDA_FIXA: CDB, LCI, LCA
                      * TESOURO_DIRETO: Tesouro IPCA, Tesouro Selic, Tesouro Prefixado (ex: "Tesouro IPCA+ 2029", "Tesouro Selic 2028")
                      * ETF: fundos de índice (ex: IVVB11, BOVA11, WRLD11)
                      * CRIPTO: criptomoedas (ex: BTC, ETH, XRP)
                    - Mapeie "tipoOperacao" para: COMPRA, VENDA, DIVIDENDO.
                    - Extraia os campos:
                      * "ticker": o ticker do ativo (ex: "BBAS3", "CXSE3", "Tesouro IPCA+ 2029"). Preserve o nome ou ticker exato do ativo de Renda Fixa ou Tesouro Direto.
                      * "quantidade": a quantidade do ativo (use números decimais se necessário, ex: 29.0, 100.0). Para proventos, representa a quantidade de cotas.
                      * "precoUnitario": preço pago ou recebido por unidade/cota (ex: 0.09).
                      * "custos": custos ou taxas se houver (caso contrário, 0.0).
                      * "valorTotal": valor total bruto da operação/provento (ex: 2.74).
                      * "valorLiquido": valor total líquido do provento após impostos (ex: 2.33).
                      * "tipoProvento": se for provento (tipoOperacao = DIVIDENDO), extraia o tipo exato: "Dividendo", "JSCP" ou "Rend. Trib." (SEMPRE no singular "Dividendo", NUNCA no plural "Dividendos").
                      * "dia": o dia do mês do lançamento se disponível no texto.
                      * "dataVencimento": a data de vencimento se disponível no texto (formato DD/MM/YYYY).
                      * "indexador": indexador se disponível no texto (ex: "IPCA", "SELIC", "CDI", "PRE").
                      * "taxa": a taxa contratada se disponível no texto (use número decimal, ex: se for "IPCA + 7,70%%", a taxa é 7.70).

                    REGRAS DE DATA:
                    - Extraia o dia se disponível no texto.
                    - Se o mês/ano estiver no texto, use-o. Caso contrário, use mes=%d e ano=%d.
                    - IMPORTANTE PARA PROVENTOS (DIVIDENDO):
                      * Se houver DUAS datas listadas na entrada do provento (ex: a data "com"/direito e a data de pagamento/recebimento), a data a ser cadastrada no campo "data" DEVE ser a data de pagamento/recebimento (a segunda data, ex: entre '25/03/2025' e '30/12/2026', a data cadastrada deve ser '2026-12-30').
                      * Se houver APENAS UMA data listada na entrada do provento (ex: '25/03/2025'), use essa única data disponível como a data a ser cadastrada no campo "data" (ex: '2025-03-25').
                      * Defina sempre o campo "dia" com o dia dessa data escolhida.
                      * MAPEAMENTO DE VALORES DE PROVENTOS:
                        - O valor pago por cada cota (ex: "R$ 0,09") deve ser o "precoUnitario".
                        - O total de cotas (ex: "29,00") deve ser a "quantidade".
                        - O valor total bruto (ex: "R$ 2,74") deve ser o "valorTotal".
                        - O valor total líquido (ex: "R$ 2,33") deve ser o "valorLiquido".
                        - Nunca use o valor unitário como o valor total. Garanta que o "valorTotal" represente o total bruto e o "valorLiquido" o total líquido.

                    Retorne APENAS um JSON válido, sem markdown, sem explicação.
                    IMPORTANTE: Para evitar quebrar a estrutura do JSON, NUNCA inclua aspas duplas não escapadas dentro de qualquer valor de string (como o campo "resumo"). Se for necessário usar aspas internas, use aspas simples (') ou escape-as estritamente como \\".

                    Formato esperado do JSON:
                    {
                      "items": [
                        {
                          "descricao": "Uber Trip",
                          "categoria": "GASTO",
                          "subcategoria": "Transporte",
                          "valor": 15.50,
                          "dia": 14
                        }
                      ],
                      "investimentos": [
                        {
                          "ticker": "BBAS3",
                          "tipoAtivo": "ACAO",
                          "tipoOperacao": "COMPRA",
                          "tipoProvento": null,
                          "quantidade": 100.0,
                          "precoUnitario": 23.45,
                          "custos": 0.0,
                          "valorTotal": 2345.0,
                          "valorLiquido": 2345.0,
                          "dia": 14,
                          "dataVencimento": null,
                          "indexador": null,
                          "taxa": null,
                          "data": "2026-12-30"
                        }
                      ],
                      "mes": %d,
                      "ano": %d,
                      "resumo": "Breve resumo do que foi encontrado"
                    }

                    TEXTO PARA ANALISAR:
                    %s
                    """
                    .formatted(subcategoriasJson, mes, ano, mes, ano, texto);

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
            log.info("Gemini raw JSON response:\n{}", resposta);
            JsonNode root = objectMapper.readTree(resposta);

            List<ParsedItem> items = new ArrayList<>();
            JsonNode itemsNode = root.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    items.add(ParsedItem.builder()
                            .descricao(item.has("descricao") ? item.get("descricao").asText() : "")
                            .categoria(item.has("categoria") ? item.get("categoria").asText() : "GASTO")
                            .subcategoria(item.has("subcategoria") ? item.get("subcategoria").asText() : "Outros")
                            .valor(item.has("valor")
                                    ? new BigDecimal(item.get("valor").asText()).setScale(2, RoundingMode.HALF_UP)
                                    : BigDecimal.ZERO)
                            .dia(item.has("dia") && !item.get("dia").isNull() ? item.get("dia").asInt() : null)
                            .build());
                }
            }

            List<ParsedInvestimentoItem> investimentos = new ArrayList<>();
            JsonNode invsNode = root.get("investimentos");
            if (invsNode != null && invsNode.isArray()) {
                for (JsonNode item : invsNode) {
                    investimentos.add(ParsedInvestimentoItem.builder()
                            .ticker(item.has("ticker") ? item.get("ticker").asText() : "")
                            .tipoAtivo(item.has("tipoAtivo") ? item.get("tipoAtivo").asText() : "ACAO")
                            .tipoOperacao(item.has("tipoOperacao") ? item.get("tipoOperacao").asText() : "COMPRA")
                            .quantidade(item.has("quantidade")
                                    ? new BigDecimal(item.get("quantidade").asText()).setScale(8, RoundingMode.HALF_UP)
                                    : BigDecimal.ZERO)
                            .precoUnitario(
                                    item.has("precoUnitario")
                                            ? new BigDecimal(item.get("precoUnitario").asText()).setScale(2,
                                                    RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO)
                            .custos(item.has("custos") && !item.get("custos").isNull()
                                    ? new BigDecimal(item.get("custos").asText()).setScale(2, RoundingMode.HALF_UP)
                                    : BigDecimal.ZERO)
                            .valorTotal(item.has("valorTotal")
                                    ? new BigDecimal(item.get("valorTotal").asText()).setScale(2, RoundingMode.HALF_UP)
                                    : BigDecimal.ZERO)
                            .valorLiquido(item.has("valorLiquido") && !item.get("valorLiquido").isNull()
                                    ? new BigDecimal(item.get("valorLiquido").asText()).setScale(2,
                                            RoundingMode.HALF_UP)
                                    : null)
                            .dia(item.has("dia") && !item.get("dia").isNull() ? item.get("dia").asInt() : null)
                            .dataVencimento(item.has("dataVencimento") && !item.get("dataVencimento").isNull()
                                    ? item.get("dataVencimento").asText()
                                    : null)
                            .indexador(item.has("indexador") && !item.get("indexador").isNull()
                                    ? item.get("indexador").asText()
                                    : null)
                            .taxa(item.has("taxa") && !item.get("taxa").isNull()
                                    ? new BigDecimal(item.get("taxa").asText()).setScale(2, RoundingMode.HALF_UP)
                                    : null)
                            .data(item.has("data") && !item.get("data").isNull() ? item.get("data").asText() : null)
                            .tipoProvento(normalizarTipoProvento(item.has("tipoProvento") && !item.get("tipoProvento").isNull()
                                    ? item.get("tipoProvento").asText()
                                    : null))
                            .build());
                }
            }

            marcarDuplicadosItens(items, mes, ano);
            marcarDuplicadosInvestimentos(investimentos, mes, ano);

            String resumo = root.has("resumo") ? root.get("resumo").asText() : "Documento processado.";

            return ParseResponse.builder()
                    .items(items)
                    .investimentos(investimentos)
                    .resumo(resumo)
                    .build();

        } catch (Exception e) {
            log.error("Erro ao parsear documento: {}", e.getMessage(), e);
            return ParseResponse.builder()
                    .error("Erro ao processar documento: " + e.getMessage())
                    .items(Collections.emptyList())
                    .investimentos(Collections.emptyList())
                    .build();
        }
    }

    private void marcarDuplicadosItens(List<ParsedItem> items, int defaultMes, int defaultAno) {
        if (items == null || items.isEmpty()) return;

        Map<String, List<Lancamento>> lancamentosPorMesAno = new HashMap<>();
        Set<Long> matchedIds = new HashSet<>();

        for (ParsedItem item : items) {
            int mes = defaultMes;
            int ano = defaultAno;

            String key = ano + "-" + mes;
            List<Lancamento> existentes = lancamentosPorMesAno.computeIfAbsent(key, k ->
                    lancamentoRepository.findByAnoAndMesOrderByCategoriaAscSubcategoriaAsc(ano, mes)
            );

            for (Lancamento l : existentes) {
                if (matchedIds.contains(l.getId())) continue;

                boolean valorIgual = l.getValor() != null && item.getValor() != null
                        && l.getValor().compareTo(item.getValor()) == 0;

                boolean diaIgual = (item.getDia() == null || l.getDia() == null || l.getDia().equals(item.getDia()));

                String normL = normalizarTexto(l.getDescricao());
                String normItem = normalizarTexto(item.getDescricao());

                boolean descIgual = normL.equals(normItem)
                        || (!normL.isEmpty() && !normItem.isEmpty() && (normL.contains(normItem) || normItem.contains(normL)))
                        || (l.getDia() != null && l.getDia().equals(item.getDia()) && normalizarTexto(l.getSubcategoria()).equalsIgnoreCase(normalizarTexto(item.getSubcategoria())));

                if (valorIgual && diaIgual && descIgual) {
                    item.setDuplicado(true);
                    matchedIds.add(l.getId());
                    break;
                }
            }
        }
    }

    private void marcarDuplicadosInvestimentos(List<ParsedInvestimentoItem> investimentos, int defaultMes, int defaultAno) {
        if (investimentos == null || investimentos.isEmpty()) return;

        List<InvestimentoLancamento> existentes = investimentoLancamentoRepository.findAllWithAtivoByOrderByDataDesc();
        Set<Long> matchedIds = new HashSet<>();

        for (ParsedInvestimentoItem inv : investimentos) {
            LocalDate targetDate = null;
            if (inv.getData() != null && !inv.getData().isBlank()) {
                try {
                    targetDate = LocalDate.parse(inv.getData().trim());
                } catch (Exception ignored) {
                }
            }
            if (targetDate == null && inv.getDia() != null) {
                try {
                    targetDate = LocalDate.of(defaultAno, defaultMes, inv.getDia());
                } catch (Exception ignored) {
                }
            }

            for (InvestimentoLancamento il : existentes) {
                if (matchedIds.contains(il.getId())) continue;

                boolean tickerIgual = il.getAtivo() != null && il.getAtivo().getTicker() != null
                        && il.getAtivo().getTicker().equalsIgnoreCase(inv.getTicker());

                boolean tipoOpIgual = il.getTipoOperacao() != null
                        && il.getTipoOperacao().name().equalsIgnoreCase(inv.getTipoOperacao());

                boolean dataIgual = targetDate != null && il.getData() != null && il.getData().equals(targetDate);
                if (!dataIgual && targetDate != null && il.getData() != null) {
                    dataIgual = il.getData().getYear() == targetDate.getYear()
                            && il.getData().getMonthValue() == targetDate.getMonthValue()
                            && il.getData().getDayOfMonth() == targetDate.getDayOfMonth();
                }

                boolean valorIgual = il.getValorTotal() != null && inv.getValorTotal() != null
                        && il.getValorTotal().compareTo(inv.getValorTotal()) == 0;

                if (tickerIgual && tipoOpIgual && dataIgual && valorIgual) {
                    inv.setDuplicado(true);
                    matchedIds.add(il.getId());
                    break;
                }
            }
        }
    }

    private String normalizarTexto(String input) {
        if (input == null) return "";
        return java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }

    public static String normalizarTipoProvento(String tp) {
        if (tp == null || tp.isBlank()) return "Dividendo";
        String tpLower = tp.trim().toLowerCase();
        if (tpLower.startsWith("div")) return "Dividendo";
        if (tpLower.contains("jcp") || tpLower.contains("jscp")) return "JSCP";
        if (tpLower.contains("rend")) return "Rend. Trib.";
        return tp.trim();
    }

    // ─── INSIGHTS AUTOMÁTICOS ────────────────────────────────────────────────

    public InsightResponse gerarInsights(int ano) {
        return gerarInsights(ano, null, null);
    }

    public InsightResponse gerarInsights(int ano, Integer mes, String tipo) {
        if (mes == null || mes <= 0 || tipo == null || tipo.isBlank()) {
            try {
                String contexto = montarContextoFinanceiro(ano, false);
                return gerarInsightsCustom("Dashboard Anual - Finanças Gerais Pessoais",
                        contexto,
                        "Foque na eficiência do fluxo de caixa e gestão geral de gastos e receitas. É expressamente proibido abordar ou analisar investimentos, ativos ou rebalanceamento de carteira.");
            } catch (Exception e) {
                log.error("Erro ao gerar insights gerais: {}", e.getMessage(), e);
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
            } else if (tipoLabel.contains("aporte") || tipoLabel.contains("transferencia")) {
                categoriaAlvo = Categoria.TRANSFERENCIA;
            } else {
                categoriaAlvo = Categoria.GASTO; // Gasto variável
            }

            final Categoria finalCat = categoriaAlvo;
            List<LancamentoDTO> lancamentos = lancamentoService.listarPorAno(ano).stream()
                    .filter(l -> l.getMes() == mes && l.getCategoria() == finalCat)
                    .toList();

            String[] mesesNomes = { "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro" };
            String mesNome = (mes >= 1 && mes <= 12) ? mesesNomes[mes - 1] : String.valueOf(mes);
            String labelCategoria = finalCat == Categoria.RECEITA ? "Receitas"
                    : finalCat == Categoria.GASTO_FIXO ? "Gastos Fixos"
                            : finalCat == Categoria.ASSINATURA ? "Assinaturas"
                                    : finalCat == Categoria.TRANSFERENCIA ? "Aportes" : "Gastos Variáveis";

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
                            Collectors.reducing(BigDecimal.ZERO, LancamentoDTO::getValor, BigDecimal::add)));

            porSubcat.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .forEach(e -> ctx.append("- ").append(e.getKey())
                            .append(": R$ ").append(formatarValor(e.getValue())).append("\n"));

            ctx.append("\nDETALHE DOS LANÇAMENTOS INDIVIDUAIS (Maiores valores primeiro):\n");
            lancamentos.stream()
                    .sorted((a, b) -> b.getValor().compareTo(a.getValor()))
                    .limit(20)
                    .forEach(l -> ctx.append("- Dia ").append(l.getDia() != null ? l.getDia() : "-")
                            .append(": ")
                            .append(l.getDescricao() != null && !l.getDescricao().isBlank() ? l.getDescricao()
                                    : l.getSubcategoria())
                            .append(" (").append(l.getSubcategoria()).append(") — R$ ")
                            .append(formatarValor(l.getValor())).append("\n"));

            String foco = "";
            if (finalCat == Categoria.ASSINATURA) {
                foco = "Identifique assinaturas subutilizadas e calcule o desperdício anual em potencial de investimento.";
            } else if (finalCat == Categoria.RECEITA) {
                foco = "Foque na maximização do fluxo de entrada, diversificação de receitas e estratégias de aumento de renda.";
            } else if (finalCat == Categoria.TRANSFERENCIA) {
                foco = "Foque na consistência dos aportes, alocação estratégica e acompanhamento da evolução do patrimônio.";
            } else {
                foco = "Foque na redução de desperdícios, otimização e controle rigoroso de despesas.";
            }

            return gerarInsightsCustom("Gestão de " + labelCategoria + " — " + mesNome + "/" + ano,
                    ctx.toString(),
                    foco);

        } catch (Exception e) {
            log.error("Erro ao gerar insights mensais por categoria: {}", e.getMessage(), e);
            return InsightResponse.builder()
                    .error("Erro ao gerar insights: " + e.getMessage())
                    .insights(Collections.emptyList())
                    .build();
        }
    }

    public InsightResponse gerarInsightsInvestimentos() {
        try {
            InvestimentoDashboardDTO dashboard = investimentoService.calcularDashboard();

            StringBuilder ctx = new StringBuilder();
            ctx.append("ANÁLISE DE PORTFÓLIO DE INVESTIMENTOS — ").append(LocalDate.now()).append("\n\n");

            ctx.append("RESUMO DA CARTEIRA:\n");
            ctx.append("- Patrimônio Total: R$ ").append(formatarValor(dashboard.getPatrimonioTotal())).append("\n");
            ctx.append("- Valor Investido: R$ ").append(formatarValor(dashboard.getValorInvestido())).append("\n");
            ctx.append("- Lucro/Prejuízo: R$ ").append(formatarValor(dashboard.getLucroTotal()))
                    .append(" (").append(formatarValor(dashboard.getVariacaoPercent())).append("%)\n");
            ctx.append("- Dividendos Recebidos: R$ ").append(formatarValor(dashboard.getDividendosTotal()))
                    .append("\n\n");

            ctx.append("DISTRIBUIÇÃO POR CLASSE DE ATIVO:\n");
            if (dashboard.getResumoPorTipo() != null) {
                dashboard.getResumoPorTipo().forEach((tipo, resumo) -> {
                    ctx.append("- ").append(tipo.name()).append(": R$ ")
                            .append(formatarValor(resumo.getValorTotal()))
                            .append(" (").append(formatarValor(resumo.getPercentCarteira())).append("% da carteira) — ")
                            .append(resumo.getQuantidadeAtivos()).append(" ativos\n");
                });
            }
            ctx.append("\n");

            ctx.append("POSIÇÕES ATUAIS (ATIVOS NA CARTEIRA):\n");
            if (dashboard.getAtivosPorTipo() != null) {
                dashboard.getAtivosPorTipo().forEach((tipo, ativos) -> {
                    if (!ativos.isEmpty()) {
                        ctx.append("\n[").append(tipo.name()).append("]:\n");
                        ativos.stream()
                                .sorted((a, b) -> b.getValorTotal().compareTo(a.getValorTotal()))
                                .forEach(a -> {
                                    ctx.append("- ").append(a.getTicker())
                                            .append(": Qtd ").append(formatarValor(a.getQuantidade()))
                                            .append(" | Preço Médio R$").append(formatarValor(a.getPrecoMedio()))
                                            .append(" | Preço Atual R$").append(formatarValor(a.getPrecoAtual()))
                                            .append(" | Variação ").append(formatarValor(a.getVariacao())).append("%")
                                            .append(" | Peso ").append(formatarValor(a.getPercentCarteira()))
                                            .append("%\n");
                                });
                    }
                });
            }

            return gerarInsightsCustom("Carteira de Investimentos",
                    ctx.toString(),
                    "Foque em rebalanceamento, diversificação e custo de oportunidade.");
        } catch (Exception e) {
            log.error("Erro ao gerar insights de investimentos: {}", e.getMessage(), e);
            return InsightResponse.builder()
                    .error("Erro ao gerar insights de investimentos: " + e.getMessage())
                    .insights(Collections.emptyList())
                    .build();
        }
    }

    // ─── MÉTODOS INTERNOS ────────────────────────────────────────────────────

    /**
     * Monta o contexto financeiro do usuário para envio ao LLM.
     */
    private String montarContextoFinanceiro(int ano, boolean incluirInvestimentos) {
        DashboardDTO dashboard = lancamentoService.calcularDashboard(ano);
        List<LancamentoDTO> lancamentos = lancamentoService.listarPorAno(ano);
        int mesAtual = LocalDate.now().getMonthValue();

        StringBuilder ctx = new StringBuilder();
        ctx.append("DADOS FINANCEIROS DO USUÁRIO — ANO ").append(ano).append("\n\n");

        // Totais anuais
        BigDecimal totalAportes = lancamentos.stream()
                .filter(l -> l.getCategoria() == Categoria.TRANSFERENCIA 
                        && !"Resgate de Investimentos".equals(l.getSubcategoria()) 
                        && !"Outras Transferências".equals(l.getSubcategoria()))
                .map(LancamentoDTO::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ctx.append("RESUMO ANUAL:\n");
        ctx.append("- Receitas anuais: R$ ").append(formatarValor(dashboard.getTotalReceitas())).append("\n");
        ctx.append("- Gastos anuais (fixos + variáveis): R$ ").append(formatarValor(dashboard.getTotalGastos()))
                .append("\n");
        ctx.append("- Assinaturas anuais: R$ ").append(formatarValor(dashboard.getTotalAssinaturas())).append("\n");
        ctx.append("- Aportes anuais: R$ ").append(formatarValor(totalAportes)).append("\n");
        ctx.append("- Saldo anual: R$ ").append(formatarValor(dashboard.getSaldoAnual())).append("\n\n");

        // Dados mensais
        String[] meses = { "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro" };

        ctx.append("RECEITAS/GASTOS/SALDO POR MÊS:\n");
        for (int i = 0; i < 12; i++) {
            BigDecimal receita = dashboard.getReceitasPorMes().get(i);
            BigDecimal gasto = dashboard.getGastosPorMes().get(i);
            BigDecimal assinatura = dashboard.getAssinaturasPorMes().get(i);
            BigDecimal saldo = dashboard.getSaldoPorMes().get(i);
            
            final int mesIdx = i + 1;
            BigDecimal aportes = lancamentos.stream()
                    .filter(l -> l.getMes() == mesIdx 
                            && l.getCategoria() == Categoria.TRANSFERENCIA 
                            && !"Resgate de Investimentos".equals(l.getSubcategoria()) 
                            && !"Outras Transferências".equals(l.getSubcategoria()))
                    .map(LancamentoDTO::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Só mostra meses com dados
            if (receita.compareTo(BigDecimal.ZERO) > 0 || gasto.compareTo(BigDecimal.ZERO) > 0 || aportes.compareTo(BigDecimal.ZERO) > 0) {
                ctx.append("- ").append(meses[i]).append(": ");
                ctx.append("Receita R$").append(formatarValor(receita));
                ctx.append(" | Gastos R$").append(formatarValor(gasto));
                ctx.append(" | Assinaturas R$").append(formatarValor(assinatura));
                ctx.append(" | Aportes R$").append(formatarValor(aportes));
                ctx.append(" | Saldo R$").append(formatarValor(saldo));
                if (i + 1 == mesAtual)
                    ctx.append(" ← MÊS ATUAL");
                ctx.append("\n");
            }
        }

        // Detalhamento de gastos mensais por subcategoria
        ctx.append("\nDETALHAMENTO DE GASTOS POR SUBCATEGORIA EM CADA MÊS:\n");
        for (int i = 0; i < 12; i++) {
            final int mesIdx = i + 1;
            List<LancamentoDTO> gastosDoMes = lancamentos.stream()
                    .filter(l -> l.getMes() == mesIdx && l.getCategoria() != Categoria.RECEITA && l.getCategoria() != Categoria.TRANSFERENCIA)
                    .toList();

            if (!gastosDoMes.isEmpty()) {
                ctx.append("- ").append(meses[i]).append(":\n");

                // Agrupa por subcategoria somando os valores
                Map<String, BigDecimal> subcatValores = gastosDoMes.stream()
                        .collect(Collectors.groupingBy(
                                LancamentoDTO::getSubcategoria,
                                Collectors.reducing(BigDecimal.ZERO, LancamentoDTO::getValor, BigDecimal::add)));

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

        // Carteira de investimentos atual
        if (incluirInvestimentos) {
            try {
                InvestimentoDashboardDTO invDashboard = investimentoService.calcularDashboard();
                ctx.append("\nCARTEIRA DE INVESTIMENTOS ATUAL:\n");
                ctx.append("- Patrimônio Total em Investimentos: R$ ")
                        .append(formatarValor(invDashboard.getPatrimonioTotal())).append("\n");
                ctx.append("- Lucro/Prejuízo Total em Investimentos: R$ ")
                        .append(formatarValor(invDashboard.getLucroTotal()))
                        .append(" (").append(formatarValor(invDashboard.getVariacaoPercent())).append("%)\n");

                if (invDashboard.getAtivosPorTipo() != null && !invDashboard.getAtivosPorTipo().isEmpty()) {
                    invDashboard.getAtivosPorTipo().forEach((tipo, list) -> {
                        if (list != null && !list.isEmpty()) {
                            ctx.append("\nClasse de Ativo: ").append(tipo.name()).append(":\n");
                            for (AtivoDTO a : list) {
                                ctx.append("  * Ticker: ").append(a.getTicker());
                                if (a.getNome() != null && !a.getNome().isBlank()) {
                                    ctx.append(" (").append(a.getNome()).append(")");
                                }
                                ctx.append(" | Qtd: ").append(formatarValor(a.getQuantidade()))
                                        .append(" | Preço Médio: R$ ").append(formatarValor(a.getPrecoMedio()))
                                        .append(" | Preço Atual: R$ ").append(formatarValor(a.getPrecoAtual()))
                                        .append(" | Valor Total: R$ ").append(formatarValor(a.getValorTotal()))
                                        .append(" | Variação: ").append(formatarValor(a.getVariacao())).append("%");
                                if (a.getDataLancamento() != null) {
                                    ctx.append(" | Data de Aquisição: ").append(a.getDataLancamento().toString());
                                }
                                ctx.append("\n");
                            }
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Erro ao incluir contexto de investimentos na IA: {}", e.getMessage());
            }
        }

        // Data atual para contexto temporal
        ctx.append("\nDATA ATUAL: ").append(LocalDate.now()).append("\n");

        return ctx.toString();
    }

    protected String chamarGemini(String prompt) {
        return chamarGemini(prompt, null);
    }

    protected String chamarGemini(String prompt, String responseMimeType) {
        AiConfig config = configRepository.findFirstByOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException(
                        "API Key de IA não configurada. Vá em Configurações para adicionar."));

        String provider = config.getProvider();
        String apiUrl = config.getApiUrl();

        boolean isGemini = (provider == null || "gemini".equalsIgnoreCase(provider))
                && (apiUrl == null || apiUrl.isBlank());

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
            body.put("max_tokens", 4096);
            body.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)));

            if (responseMimeType != null && "application/json".equalsIgnoreCase(responseMimeType)) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            headers.set("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
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
                        Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig);

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
                        throw new RuntimeException(
                                "A chave de API atingiu o limite de gastos mensal (spending cap) configurado no Google AI Studio. Acesse https://aistudio.google.com/ e configure um limite de gastos adequado.");
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
        if (valor == null)
            return "0,00";
        return valor.setScale(2, RoundingMode.HALF_UP).toString().replace(".", ",");
    }
}
