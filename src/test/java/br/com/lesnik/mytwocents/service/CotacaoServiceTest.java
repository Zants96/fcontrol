package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import br.com.lesnik.mytwocents.repository.AtivoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CotacaoServiceTest {

    @Test
    void testBuscarCotacaoCriptoBTC() throws Exception {
        AtivoRepository repository = Mockito.mock(AtivoRepository.class);
        AiConfigRepository aiConfigRepository = Mockito.mock(AiConfigRepository.class);
        Mockito.when(aiConfigRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());
        CotacaoService service = new CotacaoService(repository, aiConfigRepository);

        var result = service.buscarCotacaoCripto("BTC", null);

        assertNotNull(result);
        assertEquals("BTC", result.preco().compareTo(BigDecimal.ZERO) > 0 ? "BTC" : "");
        assertTrue(result.preco().compareTo(BigDecimal.ZERO) > 0);
        assertEquals("Criptomoedas", result.sector());
        assertEquals("https://assets.coingecko.com/coins/images/1/large/bitcoin.png", result.logoUrl());
    }

    @Test
    void testBuscarCotacaoCriptoETH() throws Exception {
        AtivoRepository repository = Mockito.mock(AtivoRepository.class);
        AiConfigRepository aiConfigRepository = Mockito.mock(AiConfigRepository.class);
        Mockito.when(aiConfigRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());
        CotacaoService service = new CotacaoService(repository, aiConfigRepository);

        var result = service.buscarCotacaoCripto("ETH", null);

        assertNotNull(result);
        assertEquals("ETH", result.preco().compareTo(BigDecimal.ZERO) > 0 ? "ETH" : "");
        assertTrue(result.preco().compareTo(BigDecimal.ZERO) > 0);
        assertEquals("Criptomoedas", result.sector());
        assertEquals("https://assets.coingecko.com/coins/images/279/large/ethereum.png", result.logoUrl());
    }
}
