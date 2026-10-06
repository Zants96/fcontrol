package br.com.lesnik.mytwocents.util;

import br.com.lesnik.mytwocents.model.Ativo;
import br.com.lesnik.mytwocents.model.CategoriaTatica;
import br.com.lesnik.mytwocents.model.TipoAtivo;

/**
 * Utilitário para inferir a categoria tática de um ativo automaticamente.
 * Regras:
 *  - CDB, Poupança ou Reserva (RENDA_FIXA com "CDB", "POUPANCA" ou "RESERVA" no ticker/nome) → SEGURANCA
 *  - Tesouro SELIC (TESOURO_DIRETO com "SELIC" no ticker/nome) → SEGURANCA
 *  - FII → RENDA
 *  - Tesouro IPCA / Prefixado → RENDA
 *  - CRIPTO e ACAO → CRESCIMENTO
 *  - ETF → GLOBAL
 *  - Fallback → RENDA
 */
public final class CategoriaTaticaUtils {

    private CategoriaTaticaUtils() {
        // Classe utilitária - não instanciável
    }

    public static CategoriaTatica inferirCategoriaTatica(Ativo a) {
        if (a == null) return CategoriaTatica.RENDA;

        String ticker = a.getTicker() != null ? a.getTicker().toUpperCase() : "";
        String nome = a.getNome() != null ? a.getNome().toUpperCase() : "";
        TipoAtivo tipo = a.getTipoAtivo();

        if (tipo == null) return CategoriaTatica.RENDA;

        // Segurança: CDB, Poupança, Reserva e Tesouro SELIC
        if (tipo == TipoAtivo.RENDA_FIXA && (ticker.contains("CDB") || nome.contains("CDB")
                || ticker.contains("POUPANCA") || nome.contains("POUPANCA")
                || ticker.contains("RESERVA") || nome.contains("RESERVA"))) {
            return CategoriaTatica.SEGURANCA;
        }
        if (tipo == TipoAtivo.TESOURO_DIRETO && (ticker.contains("SELIC") || nome.contains("SELIC"))) {
            return CategoriaTatica.SEGURANCA;
        }

        // Renda: FIIs e Tesouro IPCA / Prefixado
        if (tipo == TipoAtivo.FII) {
            return CategoriaTatica.RENDA;
        }
        if (tipo == TipoAtivo.TESOURO_DIRETO && (ticker.contains("IPCA") || nome.contains("IPCA") || ticker.contains("PRE") || nome.contains("PRE"))) {
            return CategoriaTatica.RENDA;
        }

        // Crescimento: Cripto e Ações
        if (tipo == TipoAtivo.CRIPTO || tipo == TipoAtivo.ACAO) {
            return CategoriaTatica.CRESCIMENTO;
        }

        // Global: ETFs
        if (tipo == TipoAtivo.ETF) {
            return CategoriaTatica.GLOBAL;
        }

        // Fallback
        return CategoriaTatica.RENDA;
    }
}