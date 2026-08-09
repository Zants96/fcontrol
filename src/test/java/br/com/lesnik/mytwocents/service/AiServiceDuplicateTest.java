package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.dto.AiChatDTO;
import br.com.lesnik.mytwocents.model.*;
import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import br.com.lesnik.mytwocents.repository.InvestimentoLancamentoRepository;
import br.com.lesnik.mytwocents.repository.LancamentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AiServiceDuplicateTest {

    @Mock
    private LancamentoRepository lancamentoRepository;

    @Mock
    private InvestimentoLancamentoRepository investimentoLancamentoRepository;

    @Mock
    private LancamentoService lancamentoService;

    @Mock
    private InvestimentoService investimentoService;

    @Mock
    private CotacaoService cotacaoService;

    @Mock
    private AiConfigRepository aiConfigRepository;

    @InjectMocks
    private AiService aiService;

    @Test
    void deveMarcarLancamentosComunsDuplicados() {
        Lancamento l1 = Lancamento.builder()
                .id(1L)
                .descricao("Uber Trip")
                .valor(new BigDecimal("25.50"))
                .ano(2026)
                .mes(8)
                .dia(15)
                .categoria(Categoria.GASTO)
                .subcategoria("Transporte")
                .build();

        when(lancamentoRepository.findByAno(2026)).thenReturn(List.of(l1));

        List<AiChatDTO.ParsedItem> itens = new ArrayList<>();
        itens.add(AiChatDTO.ParsedItem.builder()
                .descricao("Uber Trip")
                .valor(new BigDecimal("25.50"))
                .dia(15)
                .categoria("GASTO")
                .subcategoria("Transporte")
                .duplicado(false)
                .build());

        itens.add(AiChatDTO.ParsedItem.builder()
                .descricao("Padaria")
                .valor(new BigDecimal("12.00"))
                .dia(15)
                .categoria("GASTO")
                .subcategoria("Alimentação")
                .duplicado(false)
                .build());

        aiService.marcarDuplicadosItens(itens, 2026, 8);

        assertTrue(itens.get(0).getDuplicado(), "Uber Trip deve ser marcado como duplicado");
        assertFalse(itens.get(1).getDuplicado(), "Padaria não deve ser marcada como duplicado");
    }

    @Test
    void deveMarcarInvestimentosDuplicados() {
        Ativo ativoBbas = Ativo.builder()
                .id(1L)
                .ticker("BBAS3")
                .tipoAtivo(TipoAtivo.ACAO)
                .build();

        InvestimentoLancamento inv1 = InvestimentoLancamento.builder()
                .id(10L)
                .ativo(ativoBbas)
                .tipoOperacao(TipoOperacao.COMPRA)
                .valorTotal(new BigDecimal("2350.00"))
                .data(LocalDate.of(2026, 8, 10))
                .build();

        when(investimentoLancamentoRepository.findAllWithAtivoByOrderByDataDesc()).thenReturn(List.of(inv1));

        List<AiChatDTO.ParsedInvestimentoItem> investimentos = new ArrayList<>();
        investimentos.add(AiChatDTO.ParsedInvestimentoItem.builder()
                .ticker("BBAS3")
                .tipoOperacao("COMPRA")
                .tipoAtivo("ACAO")
                .valorTotal(new BigDecimal("2350.00"))
                .dia(10)
                .duplicado(false)
                .build());

        investimentos.add(AiChatDTO.ParsedInvestimentoItem.builder()
                .ticker("PETR4")
                .tipoOperacao("COMPRA")
                .tipoAtivo("ACAO")
                .valorTotal(new BigDecimal("1750.00"))
                .dia(10)
                .duplicado(false)
                .build());

        aiService.marcarDuplicadosInvestimentos(investimentos, 2026, 8);

        assertTrue(investimentos.get(0).getDuplicado(), "BBAS3 deve ser marcado como duplicado");
        assertFalse(investimentos.get(1).getDuplicado(), "PETR4 não deve ser marcado como duplicado");
    }
}
