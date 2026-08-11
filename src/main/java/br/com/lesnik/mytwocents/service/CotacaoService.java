package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.model.AiConfig;
import br.com.lesnik.mytwocents.model.Ativo;
import br.com.lesnik.mytwocents.model.TipoAtivo;
import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import br.com.lesnik.mytwocents.repository.AtivoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço de cotações via BrAPI.dev
 * Suporta Ações, FIIs, ETFs e Criptomoedas no plano gratuito.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CotacaoService {

    private static final String BRAPI_QUOTE_URL = "https://brapi.dev/api/quote/";
    private static final String BRAPI_CRYPTO_URL = "https://brapi.dev/api/v2/crypto?coin=";
    private static final Duration CACHE_DURATION = Duration.ofMinutes(30);

    private static final String COINGECKO_API_KEY = "CG-2xfzwvSvpTHnc91tqN7UA5WT";

    private static final Map<String, String> COINGECKO_ID_MAP = Map.ofEntries(
        Map.entry("BTC", "bitcoin"),
        Map.entry("ETH", "ethereum"),
        Map.entry("USDT", "tether"),
        Map.entry("SOL", "solana"),
        Map.entry("XRP", "ripple"),
        Map.entry("ADA", "cardano"),
        Map.entry("DOT", "polkadot"),
        Map.entry("DOGE", "dogecoin"),
        Map.entry("AVAX", "avalanche-2"),
        Map.entry("MATIC", "matic-network"),
        Map.entry("POL", "polygon"),
        Map.entry("LINK", "chainlink"),
        Map.entry("SHIB", "shiba-inu"),
        Map.entry("LTC", "litecoin"),
        Map.entry("BCH", "bitcoin-cash"),
        Map.entry("XLM", "stellar"),
        Map.entry("ALGO", "algorand"),
        Map.entry("ATOM", "cosmos"),
        Map.entry("UNI", "uniswap"),
        Map.entry("ICP", "internet-computer"),
        Map.entry("FIL", "filecoin"),
        Map.entry("HBAR", "hedera-hashgraph"),
        Map.entry("VET", "vechain"),
        Map.entry("NEAR", "near"),
        Map.entry("LDO", "lido-dao"),
        Map.entry("GRT", "the-graph"),
        Map.entry("AAVE", "aave"),
        Map.entry("MKR", "maker"),
        Map.entry("IMX", "immutable-x"),
        Map.entry("OP", "optimism"),
        Map.entry("ARB", "arbitrum"),
        Map.entry("RNDR", "render-token"),
        Map.entry("TIA", "celestia"),
        Map.entry("SUI", "sui"),
        Map.entry("SEI", "sei-network"),
        Map.entry("FTM", "fantom"),
        Map.entry("INJ", "injective-protocol"),
        Map.entry("RENDER", "render-token")
    );

    private static final Map<String, String> COINGECKO_LOGO_MAP = Map.ofEntries(
        Map.entry("BTC", "https://assets.coingecko.com/coins/images/1/large/bitcoin.png"),
        Map.entry("ETH", "https://assets.coingecko.com/coins/images/279/large/ethereum.png"),
        Map.entry("USDT", "https://assets.coingecko.com/coins/images/325/large/tether.png"),
        Map.entry("SOL", "https://assets.coingecko.com/coins/images/4128/large/solana.png"),
        Map.entry("XRP", "https://assets.coingecko.com/coins/images/44/large/xrp-symbol-white-128.png"),
        Map.entry("ADA", "https://assets.coingecko.com/coins/images/975/large/cardano.png"),
        Map.entry("DOT", "https://assets.coingecko.com/coins/images/12171/large/polkadot-new-dot-logo.png"),
        Map.entry("DOGE", "https://assets.coingecko.com/coins/images/7829/large/dogecoin.png"),
        Map.entry("AVAX", "https://assets.coingecko.com/coins/images/12559/large/Avalanche_Circle_RedLogo_Trans.png"),
        Map.entry("LINK", "https://assets.coingecko.com/coins/images/877/large/chainlink-link-logo.png")
    );

    private final AtivoRepository ativoRepository;
    private final AiConfigRepository aiConfigRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Cache simples: ticker → (preço, timestamp) */
    private final Map<String, CachedPrice> cache = new ConcurrentHashMap<>();

    private BigDecimal cachedSelic;
    private BigDecimal cachedIpca;
    private Instant lastSelicFetch;
    private Instant lastIpcaFetch;
    private static final Duration MACRO_CACHE_DURATION = Duration.ofHours(24);

    private record CachedPrice(BigDecimal preco, Instant timestamp) {
        boolean isValid() {
            return Instant.now().isBefore(timestamp.plus(CACHE_DURATION));
        }
    }

    /**
     * Atualiza as cotações de todos os ativos ativos via BrAPI.
     * 
     * @param brapiToken token da conta gratuita do BrAPI
     * @return número de ativos atualizados com sucesso
     */
    @Transactional
    public int atualizarCotacoes(String brapiToken) {
        if (brapiToken == null || brapiToken.isBlank()) {
            log.warn("Token BrAPI não configurado. Cotações não serão atualizadas.");
            return 0;
        }

        List<Ativo> ativos = ativoRepository.findByAtivoTrueOrderByTipoAtivoAscTickerAsc();
        int atualizados = 0;

        for (Ativo ativo : ativos) {
            try {
                // Verifica cache
                CachedPrice cached = cache.get(ativo.getTicker());
                boolean nomeFaltando = (ativo.getNome() == null || ativo.getNome().isBlank()
                        || ativo.getNome().equalsIgnoreCase(ativo.getTicker()));

                if (!nomeFaltando && cached != null && cached.isValid()) {
                    ativo.setPrecoAtual(cached.preco());
                    ativoRepository.save(ativo);
                    atualizados++;
                    continue;
                }

                CotacaoResult result = buscarCotacao(ativo.getTicker(), ativo.getTipoAtivo(), brapiToken);
                if (result != null && result.preco().compareTo(BigDecimal.ZERO) > 0) {
                    ativo.setPrecoAtual(result.preco());
                    // Atualiza o nome se a API retornou
                    if (result.nome() != null && !result.nome().isBlank()) {
                        ativo.setNome(result.nome());
                    }
                    // Atualiza o logo se a API retornou
                    if (result.logoUrl() != null && !result.logoUrl().isBlank()) {
                        ativo.setLogoUrl(result.logoUrl());
                    }
                    // Atualiza o setor se a API retornou
                    if (result.sector() != null && !result.sector().isBlank()) {
                        ativo.setSector(result.sector());
                    }
                    // Atualiza o nome completo se a API retornou
                    if (result.longName() != null && !result.longName().isBlank()) {
                        ativo.setLongName(result.longName());
                    }
                    // Atualiza o dividend yield se a API retornou
                    if (result.dividendYield() != null) {
                        ativo.setDividendYield(result.dividendYield());
                    }
                    ativoRepository.save(ativo);
                    cache.put(ativo.getTicker(), new CachedPrice(result.preco(), Instant.now()));
                    atualizados++;
                    log.info("Cotação atualizada: {} = R$ {} ({})", ativo.getTicker(), result.preco(), result.nome());
                }
            } catch (Exception e) {
                log.error("Erro ao buscar cotação de {}: {}", ativo.getTicker(), e.getMessage());
            }
        }

        return atualizados;
    }

    record CotacaoResult(BigDecimal preco, String nome, String logoUrl, String sector, String longName, BigDecimal dividendYield) {
    }

    /**
     * Busca a cotação de um ativo específico.
     */
    private CotacaoResult buscarCotacao(String ticker, TipoAtivo tipo, String token) throws Exception {
        if (tipo == TipoAtivo.CRIPTO) {
            return buscarCotacaoCripto(ticker, token);
        }

        // Para Renda Fixa e Tesouro Direto, não há endpoint gratuito
        if (tipo == TipoAtivo.RENDA_FIXA || tipo == TipoAtivo.TESOURO_DIRETO) {
            log.debug("Tipo {} não suportado para cotação automática (plano free)", tipo);
            return null;
        }

        // Ações, FIIs, ETFs → endpoint padrão de quote com dados fundamentais
        String url = BRAPI_QUOTE_URL + ticker + "?token=" + token + "&fundamental=true";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("BrAPI retornou status {} para {}", response.statusCode(), ticker);
            return null;
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode results = root.path("results");
        if (results.isArray() && !results.isEmpty()) {
            JsonNode first = results.get(0);
            double price = first.path("regularMarketPrice").asDouble(0);

            String name = null;
            if (first.hasNonNull("shortName")) {
                name = first.get("shortName").asText();
            } else if (first.hasNonNull("longName")) {
                name = formatarNomeCurto(first.get("longName").asText());
            } else if (first.hasNonNull("name")) {
                name = formatarNomeCurto(first.get("name").asText());
            }

            String logoUrl = null;
            if (first.hasNonNull("logourl")) {
                logoUrl = first.get("logourl").asText();
            }

            String sector = null;
            if (first.hasNonNull("sector")) {
                sector = first.get("sector").asText();
            } else if (first.hasNonNull("segment")) {
                sector = first.get("segment").asText();
            } else if (first.hasNonNull("segmentoAtuacao")) {
                sector = first.get("segmentoAtuacao").asText();
            } else if (first.has("summaryProfile")) {
                JsonNode profile = first.path("summaryProfile");
                if (profile.hasNonNull("sector")) {
                    sector = profile.get("sector").asText();
                } else if (profile.hasNonNull("segment")) {
                    sector = profile.get("segment").asText();
                } else if (profile.hasNonNull("segmentoAtuacao")) {
                    sector = profile.get("segmentoAtuacao").asText();
                }
            }

            String longName = null;
            if (first.hasNonNull("longName")) {
                longName = first.get("longName").asText();
            } else if (first.hasNonNull("name")) {
                longName = first.get("name").asText();
            } else if (first.hasNonNull("shortName")) {
                longName = first.get("shortName").asText();
            }

            BigDecimal dy = BigDecimal.ZERO;
            if (first.hasNonNull("dividendYield")) {
                double dyRaw = first.get("dividendYield").asDouble(0.0);
                dy = BigDecimal.valueOf(dyRaw).multiply(BigDecimal.valueOf(100)).setScale(4, java.math.RoundingMode.HALF_UP);
            } else if (first.has("defaultKeyStatistics") && first.get("defaultKeyStatistics").hasNonNull("dividendYield")) {
                double dyRaw = first.get("defaultKeyStatistics").get("dividendYield").asDouble(0.0);
                dy = BigDecimal.valueOf(dyRaw).multiply(BigDecimal.valueOf(100)).setScale(4, java.math.RoundingMode.HALF_UP);
            }

            if (price > 0) {
                BigDecimal p = BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP);
                if (sector == null || sector.isBlank() || sector.equalsIgnoreCase("null")) {
                    sector = buscarSectorNoList(ticker, token);
                }
                sector = traduzirSetor(sector, ticker, tipo);
                return new CotacaoResult(p, name, logoUrl, sector, longName, dy);
            }
        }

        return null;
    }

    private String buscarSectorNoList(String ticker, String token) {
        try {
            String url = "https://brapi.dev/api/quote/list?search=" + ticker + "&token=" + token;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode stocks = root.path("stocks");
                if (stocks.isArray() && !stocks.isEmpty()) {
                    for (JsonNode stock : stocks) {
                        if (ticker.equalsIgnoreCase(stock.path("stock").asText())) {
                            if (stock.hasNonNull("sector")) {
                                return stock.get("sector").asText();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar setor no fallback para {}: {}", ticker, e.getMessage());
        }
        return null;
    }

    private String traduzirSetor(String sector, String ticker, TipoAtivo tipo) {
        if (ticker != null) {
            String t = ticker.toUpperCase().trim();
            if (t.startsWith("RZTR")) return "Agronegócio (Terra)";
            if (t.startsWith("XPML")) return "Shoppings";
            if (t.startsWith("IFRI")) return "Infraestrutura";
            if (t.startsWith("MXRF")) return "Papéis / Recebíveis";
            if (t.startsWith("HGLG")) return "Galpões Logísticos";
            if (t.startsWith("KNCR")) return "Papéis / Recebíveis";
            if (t.startsWith("HGRU")) return "Renda Urbana";
            if (t.startsWith("VISC")) return "Shoppings";
            if (t.startsWith("BTLG")) return "Galpões Logísticos";
            if (t.startsWith("ALZR")) return "Híbrido";
            if (t.startsWith("PETR")) return "Petróleo / Gás";
            if (t.startsWith("VALE")) return "Mineração";
            if (t.startsWith("BBAS") || t.startsWith("ITUB") || t.startsWith("BBDC") || t.startsWith("SANB")) return "Financeiro";
            if (t.startsWith("TAEE") || t.startsWith("TRPL") || t.startsWith("EGIE") || t.startsWith("CPLE")) return "Utilidade Pública (Energia)";
        }

        if (sector == null || sector.isBlank() || sector.equalsIgnoreCase("null")) {
            return "-";
        }

        // Tradução simples
        String s = sector.trim();
        switch (s) {
            case "Finance": return "Financeiro";
            case "Energy Minerals": return "Energia (Petróleo/Gás)";
            case "Utilities": return "Utilidade Pública";
            case "Retail Trade": return "Comércio Varejista";
            case "Health Services": return "Saúde";
            case "Consumer Services": return "Serviços ao Consumidor";
            case "Consumer Non-Durables": return "Bens de Consumo Não-Duráveis";
            case "Non-Energy Minerals": return "Mineração / Metalurgia";
            case "Commercial Services": return "Serviços Comerciais";
            case "Distribution Services": return "Distribuição";
            case "Transportation": return "Transporte / Logística";
            case "Technology Services": return "Tecnologia / TI";
            case "Process Industries": return "Indústria de Processo";
            case "Communications": return "Telecomunicações";
            case "Producer Manufacturing": return "Manufatura de Produção";
            case "Electronic Technology": return "Tecnologia Eletrônica";
            case "Industrial Services": return "Serviços Industriais";
            case "Health Technology": return "Tecnologia de Saúde";
            case "Consumer Durables": return "Bens de Consumo Duráveis";
            case "Miscellaneous": return "Diversos";
            case "Real Estate": return "Imobiliário";
            default: return s;
        }
    }

    /**
     * Busca cotação de criptomoeda via CoinGecko.
     */
    CotacaoResult buscarCotacaoCripto(String ticker, String token) throws Exception {
        String coingeckoId = COINGECKO_ID_MAP.getOrDefault(ticker.toUpperCase().trim(), ticker.toLowerCase().trim());
        String dbKey = aiConfigRepository.findFirstByOrderByIdDesc()
                .map(AiConfig::getCoingeckoKey)
                .orElse(null);
        String apiKey = (dbKey != null && !dbKey.isBlank()) ? dbKey.trim() : COINGECKO_API_KEY;
        String url = "https://api.coingecko.com/api/v3/simple/price?vs_currencies=brl,usd&ids=" + coingeckoId + "&x_cg_demo_api_key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("CoinGecko retornou status {} para {}", response.statusCode(), ticker);
            return null;
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode coinNode = root.path(coingeckoId);
        if (coinNode.isMissingNode() || coinNode.isNull()) {
            log.warn("CoinGecko não encontrou dados para id: {}", coingeckoId);
            return null;
        }

        double price = coinNode.path("brl").asDouble(0);
        if (price == 0) {
            price = coinNode.path("usd").asDouble(0);
        }

        if (price > 0) {
            String name = ticker.toUpperCase().trim();
            String logoUrl = COINGECKO_LOGO_MAP.get(ticker.toUpperCase().trim());
            String sector = "Criptomoedas";
            String longName = coingeckoId.substring(0, 1).toUpperCase() + coingeckoId.substring(1);

            BigDecimal p = BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP);
            return new CotacaoResult(p, name, logoUrl, sector, longName, BigDecimal.ZERO);
        }

        return null;
    }

    private String formatarNomeCurto(String nome) {
        if (nome == null)
            return null;
        String curto = nome
                .replaceAll("(?i)\\s+S\\.?A\\.?\\b", "")
                .replaceAll("(?i)\\bS\\.?A\\.?\\b", "")
                .replaceAll("(?i)\\s+Pfd\\b", "")
                .replaceAll("(?i)\\s+ON\\b", "")
                .replaceAll("(?i)\\s+PN\\b", "")
                .replaceAll("(?i)\\s+Fundo de Investimento Imobili[aá]rio.*", "")
                .replaceAll("(?i)\\s+FII\\b", "")
                .replaceAll("(?i)\\s+-.*", "")
                .trim();

        String[] palavras = curto.split("\\s+");
        if (palavras.length > 3) {
            curto = palavras[0] + " " + palavras[1] + " " + palavras[2];
        }

        if (curto.length() > 25) {
            curto = curto.substring(0, 25).trim();
        }
        return curto;
    }

    private BigDecimal getRateFromBCB(String seriesCode, BigDecimal fallbackValue) {
        try {
            String url = "https://api.bcb.gov.br/dados/serie/bcdata.sgs." + seriesCode + "/dados/ultimos/1?formato=json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body().trim();
                if (!body.startsWith("[") && !body.startsWith("{")) {
                    log.warn("Resposta da série BCB {} não é um JSON válido. Status: {}, Corpo: {}", 
                            seriesCode, response.statusCode(), body.length() > 100 ? body.substring(0, 100) : body);
                    return fallbackValue;
                }
                JsonNode root = objectMapper.readTree(body);
                if (root.isArray() && !root.isEmpty()) {
                    JsonNode first = root.get(0);
                    String valStr = first.path("valor").asText();
                    if (valStr != null && !valStr.isBlank()) {
                        return new BigDecimal(valStr).setScale(2, java.math.RoundingMode.HALF_UP);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro ao buscar série BCB {}: {}", seriesCode, e.getMessage());
        }
        return fallbackValue;
    }

    @Cacheable(value = "macroRates", key = "'selic'")
    public BigDecimal getSelicRate(String token) {
        if (cachedSelic != null && lastSelicFetch != null &&
                Duration.between(lastSelicFetch, Instant.now()).compareTo(MACRO_CACHE_DURATION) < 0) {
            return cachedSelic;
        }

        // Tenta Banco Central do Brasil (BCB) primeiro (100% gratuito e oficial)
        BigDecimal rate = getRateFromBCB("432", null);
        if (rate != null) {
            cachedSelic = rate;
            lastSelicFetch = Instant.now();
            log.info("Taxa Selic atualizada via Banco Central do Brasil (SGS 432): {}", cachedSelic);
            return cachedSelic;
        }

        // Fallback para BrAPI se houver token
        if (token != null && !token.isBlank()) {
            try {
                String url = "https://brapi.dev/api/v2/prime-rate?country=brazil&historical=false&token=" + token;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode nodes = root.has("results") ? root.get("results") : root.get("prime-rate");
                    if (nodes != null && nodes.isArray() && !nodes.isEmpty()) {
                        JsonNode first = nodes.get(0);
                        double r = first.path("value").asDouble(first.path("rate").asDouble(0));
                        if (r > 0) {
                            cachedSelic = BigDecimal.valueOf(r).setScale(2, java.math.RoundingMode.HALF_UP);
                            lastSelicFetch = Instant.now();
                            log.info("Taxa Selic atualizada via BrAPI: {}", cachedSelic);
                            return cachedSelic;
                        }
                    }
                } else {
                    log.warn("BrAPI prime-rate retornou status {} para Selic", response.statusCode());
                }
            } catch (Exception e) {
                log.error("Erro ao buscar taxa Selic da BrAPI: {}", e.getMessage());
            }
        }

        // Fallback final
        if (cachedSelic != null) return cachedSelic;
        return BigDecimal.valueOf(10.75); // Selic de fallback
    }

    @Cacheable(value = "macroRates", key = "'ipca'")
    public BigDecimal getIpcaRate(String token) {
        if (cachedIpca != null && lastIpcaFetch != null &&
                Duration.between(lastIpcaFetch, Instant.now()).compareTo(MACRO_CACHE_DURATION) < 0) {
            return cachedIpca;
        }

        // Tenta Banco Central do Brasil (BCB) primeiro (100% gratuito e oficial)
        BigDecimal rate = getRateFromBCB("13522", null);
        if (rate != null) {
            cachedIpca = rate;
            lastIpcaFetch = Instant.now();
            log.info("Taxa IPCA atualizada via Banco Central do Brasil (SGS 13522): {}", cachedIpca);
            return cachedIpca;
        }

        // Fallback para BrAPI se houver token
        if (token != null && !token.isBlank()) {
            try {
                String url = "https://brapi.dev/api/v2/inflation?country=brazil&historical=false&token=" + token;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode nodes = root.has("results") ? root.get("results") : root.get("inflation");
                    if (nodes != null && nodes.isArray() && !nodes.isEmpty()) {
                        JsonNode first = nodes.get(0);
                        double r = first.path("value").asDouble(first.path("rate").asDouble(0));
                        if (r > 0) {
                            cachedIpca = BigDecimal.valueOf(r).setScale(2, java.math.RoundingMode.HALF_UP);
                            lastIpcaFetch = Instant.now();
                            log.info("Taxa IPCA atualizada via BrAPI: {}", cachedIpca);
                            return cachedIpca;
                        }
                    }
                } else {
                    log.warn("BrAPI inflation retornou status {} para IPCA", response.statusCode());
                }
            } catch (Exception e) {
                log.error("Erro ao buscar taxa IPCA da BrAPI: {}", e.getMessage());
            }
        }

        // Fallback final
        if (cachedIpca != null) return cachedIpca;
        return BigDecimal.valueOf(4.50); // IPCA de fallback
    }
}
