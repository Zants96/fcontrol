package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.model.Ativo;
import br.com.lesnik.mytwocents.model.TipoAtivo;
import br.com.lesnik.mytwocents.repository.AtivoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final AtivoRepository ativoRepository;
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

    private record CotacaoResult(BigDecimal preco, String nome, String logoUrl, String sector, String longName) {
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

            if (price > 0) {
                BigDecimal p = BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP);
                if (sector == null || sector.isBlank() || sector.equalsIgnoreCase("null")) {
                    sector = buscarSectorNoList(ticker, token);
                }
                sector = traduzirSetor(sector, ticker, tipo);
                return new CotacaoResult(p, name, logoUrl, sector, longName);
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
     * Busca cotação de criptomoeda via endpoint específico da BrAPI.
     */
    private CotacaoResult buscarCotacaoCripto(String ticker, String token) throws Exception {
        String url = BRAPI_CRYPTO_URL + ticker + "&currency=BRL&token=" + token;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("BrAPI crypto retornou status {} para {}", response.statusCode(), ticker);
            return null;
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode coins = root.path("coins");
        if (coins.isArray() && !coins.isEmpty()) {
            JsonNode first = coins.get(0);
            double price = first.path("regularMarketPrice").asDouble(0);
            String name = first.path("coinName").asText(null);
            String logoUrl = first.path("coinImageUrl").asText(null);
            String sector = "Criptomoedas";
            String longName = name;
            if (price > 0) {
                BigDecimal p = BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP);
                return new CotacaoResult(p, name, logoUrl, sector, longName);
            }
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
                JsonNode root = objectMapper.readTree(response.body());
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

    public BigDecimal getSelicRate(String token) {
        if (cachedSelic != null && lastSelicFetch != null &&
                Duration.between(lastSelicFetch, Instant.now()).compareTo(MACRO_CACHE_DURATION) < 0) {
            return cachedSelic;
        }

        // Tenta Banco Central do Brasil (BCB) primeiro (100% gratuito e oficial)
        BigDecimal rate = getRateFromBCB("1178", null);
        if (rate != null) {
            cachedSelic = rate;
            lastSelicFetch = Instant.now();
            log.info("Taxa Selic atualizada via Banco Central do Brasil (SGS 1178): {}", cachedSelic);
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
