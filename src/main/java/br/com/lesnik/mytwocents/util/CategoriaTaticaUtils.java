package br.com.lesnik.mytwocents.util;

import br.com.lesnik.mytwocents.model.Ativo;
import br.com.lesnik.mytwocents.model.CategoriaTatica;
import br.com.lesnik.mytwocents.model.TipoAtivo;

/**
 * Utilitário para inferir a categoria tática de um ativo automaticamente.
 * Regras:
 *  - CDB (RENDA_FIXA com "CDB" no ticker) → SEGURANCA
 *  - Tesouro SELIC (TESOURO_DIRETO com "SELIC" no ticker) → SEGURANCA
 *  - FII → RENDA
 *  - Tesouro IPCA (TESOURO_DIRETO com "IPCA" no ticker) → RENDA
 *  - CRIPTO → CRESCIMENTO
 *  - ACAO → CRESCIMENTO
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
        TipoAtivo tipo = a.getTipoAtivo();

        if (tipo == null) return CategoriaTatica.RENDA;

        // Segurança: CDB e Tesouro SELIC
        if (tipo == TipoAtivo.RENDA_FIXA && ticker.contains("CDB")) {
            return CategoriaTatica.SEGURANCA;
        }
        if (tipo == TipoAtivo.TESOURO_DIRETO && ticker.contains("SELIC")) {
            return CategoriaTatica.SEGURANCA;
        }

        // Renda: FIIs e Tesouro IPCA
        if (tipo == TipoAtivo.FII) {
            return CategoriaTatica.RENDA;
        }
        if (tipo == TipoAtivo.TESOURO_DIRETO && ticker.contains("IPCA")) {
            return CategoriaTatica.RENDA;
        }

        // Crescimento: Cripto e Ações
        if (tipo == TipoAtivo.CRIPTO) {
            return CategoriaTatica.CRESCIMENTO;
        }
        if (tipo == TipoAtivo.ACAO) {
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