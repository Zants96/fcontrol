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

    private record CachedPrice(BigDecimal preco, Instant timestamp) {
        boolean isValid() {
            return Instant.now().isBefore(timestamp.plus(CACHE_DURATION));
        }
    }

    /**
     * Atualiza as cotações de todos os ativos ativos via BrAPI.
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
                boolean nomeFaltando = (ativo.getNome() == null || ativo.getNome().isBlank() || ativo.getNome().equalsIgnoreCase(ativo.getTicker()));
                
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

    private record CotacaoResult(BigDecimal preco, String nome) {}

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

        // Ações, FIIs, ETFs → endpoint padrão de quote
        String url = BRAPI_QUOTE_URL + ticker + "?token=" + token;

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
            if (first.hasNonNull("longName")) {
                name = formatarNomeCurto(first.get("longName").asText());
            } else if (first.hasNonNull("shortName")) {
                name = first.get("shortName").asText();
            }

            if (price > 0) {
                BigDecimal p = BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP);
                return new CotacaoResult(p, name);
            }
        }

        return null;
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
            if (price > 0) {
                BigDecimal p = BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP);
                return new CotacaoResult(p, name);
            }
        }

        return null;
    }

    private String formatarNomeCurto(String nome) {
        if (nome == null) return null;
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
}
