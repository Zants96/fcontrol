package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.dto.AiChatDTO.*;
import br.com.lesnik.mytwocents.model.Categoria;
import br.com.lesnik.mytwocents.model.Lancamento;
import br.com.lesnik.mytwocents.model.InvestimentoLancamento;
import br.com.lesnik.mytwocents.model.Ativo;
import br.com.lesnik.mytwocents.model.TipoOperacao;
import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import br.com.lesnik.mytwocents.repository.InvestimentoLancamentoRepository;
import br.com.lesnik.mytwocents.repository.LancamentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceDuplicateTest {

    @Mock
    private AiConfigRepository configRepository;

    @Mock
    private LancamentoService lancamentoService;

    @Mock
    private InvestimentoService investimentoService;

    @Mock
    private LancamentoRepository lancamentoRepository;

    @Mock
    private InvestimentoLancamentoRepository investimentoLancamentoRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    @InjectMocks
    private AiService aiService;

    @Test
    @DisplayName("Deve identificar lançamentos comuns duplicados com base em valor, dia e descrição")
    void deveMarcarLancamentosComunsDuplicados() throws Exception {
        int ano = 2026;
        int mes = 8;

        Lancamento existente = Lancamento.builder()
                .id(1L)
                .descricao("Uber Trip Sao Paulo")
                .categoria(Categoria.GASTO)
                .subcategoria("Transporte")
                .valor(new BigDecimal("25.50"))
                .mes(mes)
                .ano(ano)
                .dia(15)
                .build();

        when(lancamentoRepository.findByAnoAndMesOrderByCategoriaAscSubcategoriaAsc(eq(ano), eq(mes)))
                .thenReturn(List.of(existente));
        when(investimentoLancamentoRepository.findAllWithAtivoByOrderByDataDesc())
                .thenReturn(Collections.emptyList());

        String jsonGemini = """
                {
                  "items": [
                    {
                      "descricao": "Uber Trip",
                      "categoria": "GASTO",
                      "subcategoria": "Transporte",
                      "valor": 25.50,
                      "dia": 15
                    },
                    {
                      "descricao": "Padaria da Esquina",
                      "categoria": "GASTO",
                      "subcategoria": "Alimentação",
                      "valor": 12.00,
                      "dia": 15
                    }
                  ],
                  "investimentos": [],
                  "resumo": "2 transações encontradas"
                }
                """;

        doReturn(jsonGemini).when(aiService).chamarGemini(anyString(), anyString());

        ParseResponse response = aiService.parsearDocumento("texto fatura", mes, ano);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(2);

        ParsedItem itemDuplicado = response.getItems().get(0);
        ParsedItem itemNovo = response.getItems().get(1);

        assertThat(itemDuplicado.getDescricao()).isEqualTo("Uber Trip");
        assertThat(itemDuplicado.getDuplicado()).isTrue();

        assertThat(itemNovo.getDescricao()).isEqualTo("Padaria da Esquina");
        assertThat(itemNovo.getDuplicado()).isFalse();
    }

    @Test
    @DisplayName("Deve identificar lançamentos de investimento duplicados")
    void deveMarcarInvestimentosDuplicados() throws Exception {
        int ano = 2026;
        int mes = 8;

        Ativo bbas3 = Ativo.builder().id(10L).ticker("BBAS3").build();
        InvestimentoLancamento existente = InvestimentoLancamento.builder()
                .id(100L)
                .ativo(bbas3)
                .tipoOperacao(TipoOperacao.COMPRA)
                .data(LocalDate.of(ano, mes, 10))
                .quantidade(new BigDecimal("100"))
                .precoUnitario(new BigDecimal("23.50"))
                .valorTotal(new BigDecimal("2350.00"))
                .build();

        when(lancamentoRepository.findByAnoAndMesOrderByCategoriaAscSubcategoriaAsc(eq(ano), eq(mes)))
                .thenReturn(Collections.emptyList());
        when(investimentoLancamentoRepository.findAllWithAtivoByOrderByDataDesc())
                .thenReturn(List.of(existente));

        String jsonGemini = """
                {
                  "items": [],
                  "investimentos": [
                    {
                      "ticker": "BBAS3",
                      "tipoAtivo": "ACAO",
                      "tipoOperacao": "COMPRA",
                      "quantidade": 100,
                      "precoUnitario": 23.50,
                      "valorTotal": 2350.00,
                      "dia": 10
                    },
                    {
                      "ticker": "PETR4",
                      "tipoAtivo": "ACAO",
                      "tipoOperacao": "COMPRA",
                      "quantidade": 50,
                      "precoUnitario": 35.00,
                      "valorTotal": 1750.00,
                      "dia": 10
                    }
                  ],
                  "resumo": "2 investimentos encontrados"
                }
                """;

        doReturn(jsonGemini).when(aiService).chamarGemini(anyString(), anyString());

        ParseResponse response = aiService.parsearDocumento("extrato corretora", mes, ano);

        assertThat(response).isNotNull();
        assertThat(response.getInvestimentos()).hasSize(2);

        ParsedInvestimentoItem invDuplicado = response.getInvestimentos().get(0);
        ParsedInvestimentoItem invNovo = response.getInvestimentos().get(1);

        assertThat(invDuplicado.getTicker()).isEqualTo("BBAS3");
        assertThat(invDuplicado.getDuplicado()).isTrue();

        assertThat(invNovo.getTicker()).isEqualTo("PETR4");
        assertThat(invNovo.getDuplicado()).isFalse();
    }
}
