package br.com.lesnik.mytwocents.util;

import br.com.lesnik.mytwocents.model.Ativo;
import br.com.lesnik.mytwocents.model.CategoriaTatica;
import br.com.lesnik.mytwocents.model.TipoAtivo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoriaTaticaUtilsTest {

    @Test
    void testAbtc11InferidoComoCrescimento() {
        Ativo ativo = Ativo.builder()
                .ticker("ABTC11")
                .tipoAtivo(TipoAtivo.CRIPTO)
                .build();

        CategoriaTatica categoria = CategoriaTaticaUtils.inferirCategoriaTatica(ativo);
        assertEquals(CategoriaTatica.CRESCIMENTO, categoria);
    }

    @Test
    void testAbtc11InferidoComoCrescimentoMesmoSeTipoForEtf() {
        Ativo ativo = Ativo.builder()
                .ticker("ABTC11")
                .tipoAtivo(TipoAtivo.ETF)
                .build();

        CategoriaTatica categoria = CategoriaTaticaUtils.inferirCategoriaTatica(ativo);
        assertEquals(CategoriaTatica.CRESCIMENTO, categoria);
    }

    @Test
    void testEtfPadraoInferidoComoGlobal() {
        Ativo ativo = Ativo.builder()
                .ticker("IVVB11")
                .tipoAtivo(TipoAtivo.ETF)
                .build();

        CategoriaTatica categoria = CategoriaTaticaUtils.inferirCategoriaTatica(ativo);
        assertEquals(CategoriaTatica.GLOBAL, categoria);
    }
}
