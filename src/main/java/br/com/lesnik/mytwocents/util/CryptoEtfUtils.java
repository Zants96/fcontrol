package br.com.lesnik.mytwocents.util;

import java.util.Collections;
import java.util.Set;

/**
 * Utilitário central para identificar ETFs negociados na B3 que representam
 * Criptomoedas (Bitcoin, Ethereum, Índices Cripto, etc.).
 *
 * Para adicionar um novo ETF de Cripto no sistema, basta incluir o ticker neste Set.
 */
public final class CryptoEtfUtils {

    private CryptoEtfUtils() {
        // Utilitário - não instanciável
    }

    private static final Set<String> CRYPTO_ETFS = Set.of(
            "ABTC11", // Investo Altshares Bitcoin
            "HASH11", // Hashdex Nasdaq Crypto Index
            "BITH11", // Hashdex Nasdaq Bitcoin
            "QBTC11", // QR CME CF Bitcoin
            "ETHE11", // Hashdex Nasdaq Ethereum
            "QETH11", // QR CME CF Ether
            "BITI11", // It Now Bloomberg Galaxy Bitcoin
            "WEB311", // Hashdex Smart Contract / Web3
            "DEFI11", // Hashdex DeFi Index
            "CRPT11"  // Empiricus Teva Criptomoedas
    );

    /**
     * Verifica se o ticker fornecido pertence a um ETF de Cripto da B3.
     */
    public static boolean isCryptoEtf(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return false;
        }
        return CRYPTO_ETFS.contains(ticker.toUpperCase().trim());
    }

    /**
     * Retorna o conjunto imutável de todos os tickers de ETFs de Cripto cadastrados.
     */
    public static Set<String> getCryptoEtfs() {
        return Collections.unmodifiableSet(CRYPTO_ETFS);
    }
}
