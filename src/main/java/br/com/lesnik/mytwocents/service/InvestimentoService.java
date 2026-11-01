package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.dto.AtivoDTO;
import br.com.lesnik.mytwocents.dto.InvestimentoDashboardDTO;
import br.com.lesnik.mytwocents.dto.InvestimentoLancamentoDTO;
import br.com.lesnik.mytwocents.model.*;
import br.com.lesnik.mytwocents.repository.AtivoRepository;
import br.com.lesnik.mytwocents.repository.InvestimentoLancamentoRepository;
import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import br.com.lesnik.mytwocents.repository.LancamentoRepository;
import br.com.lesnik.mytwocents.repository.PatrimonioHistoricoRepository;
import br.com.lesnik.mytwocents.util.CategoriaTaticaUtils;
import br.com.lesnik.mytwocents.util.CryptoEtfUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvestimentoService {

    private final AtivoRepository ativoRepository;
    private final InvestimentoLancamentoRepository lancamentoRepository;
    private final AiConfigRepository aiConfigRepository;
    private final SniperEngineService sniperEngineService;
    private final LancamentoRepository financeiroRepository;
    private final CotacaoService cotacaoService;
    private final PatrimonioHistoricoRepository patrimonioHistoricoRepository;
    private final InvestimentoCalculoService investimentoCalculoService;


    // ─── CRUD DE LANÇAMENTOS ─────────────────────────────────────────────────

    @Transactional
    public InvestimentoLancamentoDTO criarLancamento(InvestimentoLancamentoDTO dto) {
        // ETFs de Criptomoeda (ABTC11, HASH11, etc.) devem ser tratados como CRIPTOMOEDA
        if (CryptoEtfUtils.isCryptoEtf(dto.getTicker())) {
            dto.setTipoAtivo(TipoAtivo.CRIPTO);
            if (dto.getCategoriaTatica() == null || dto.getCategoriaTatica() == CategoriaTatica.GLOBAL) {
                dto.setCategoriaTatica(CategoriaTatica.CRESCIMENTO);
            }
        }

        // Busca ou cria o ativo
        Ativo ativo = ativoRepository.findByTickerIgnoreCase(dto.getTicker())
                .orElseGet(() -> {
                    TipoAtivo effectiveTipo = CryptoEtfUtils.isCryptoEtf(dto.getTicker()) ? TipoAtivo.CRIPTO : dto.getTipoAtivo();
                    CategoriaTatica effectiveCat = CryptoEtfUtils.isCryptoEtf(dto.getTicker()) ? CategoriaTatica.CRESCIMENTO : dto.getCategoriaTatica();
                    Ativo novo = Ativo.builder()
                            .ticker(dto.getTicker().toUpperCase().trim())
                            .tipoAtivo(effectiveTipo)
                            .categoriaTatica(effectiveCat)
                            .quantidade(BigDecimal.ZERO)
                            .precoMedio(BigDecimal.ZERO)
                            .precoAtual(BigDecimal.ZERO)
                            .dividendosTotal(BigDecimal.ZERO)
                            .ativo(true)
                            .build();
                    return ativoRepository.save(novo);
                });

        if (CryptoEtfUtils.isCryptoEtf(ativo.getTicker())) {
            if (ativo.getTipoAtivo() != TipoAtivo.CRIPTO) {
                ativo.setTipoAtivo(TipoAtivo.CRIPTO);
            }
            if (ativo.getCategoriaTatica() == null || ativo.getCategoriaTatica() == CategoriaTatica.GLOBAL) {
                ativo.setCategoriaTatica(CategoriaTatica.CRESCIMENTO);
            }
            ativo = ativoRepository.save(ativo);
        } else if (dto.getTipoAtivo() != null && ativo.getTipoAtivo() != dto.getTipoAtivo()) {
            ativo.setTipoAtivo(dto.getTipoAtivo());
            ativo = ativoRepository.save(ativo);
        }

        if (dto.getCategoriaTatica() != null) {
            ativo.setCategoriaTatica(dto.getCategoriaTatica());
            ativo = ativoRepository.save(ativo);
        }

        BigDecimal quantidade = dto.getQuantidade() != null ? dto.getQuantidade() : BigDecimal.ZERO;
        BigDecimal precoUnit = dto.getPrecoUnitario() != null ? dto.getPrecoUnitario() : BigDecimal.ZERO;
        BigDecimal custos = dto.getCustos() != null ? dto.getCustos() : BigDecimal.ZERO;

        BigDecimal valorTotal;
        if (dto.getTipoOperacao() == TipoOperacao.DIVIDENDO) {
            if (dto.getValorTotal() != null && dto.getValorTotal().compareTo(BigDecimal.ZERO) > 0) {
                valorTotal = dto.getValorTotal();
            } else {
                valorTotal = precoUnit.add(custos);
            }
        } else {
            valorTotal = quantidade.multiply(precoUnit).add(custos);
        }

        BigDecimal valorLiquido = dto.getValorLiquido();
        if (dto.getTipoOperacao() == TipoOperacao.DIVIDENDO) {
            if ("JSCP".equalsIgnoreCase(dto.getTipoProvento())) {
                if (valorLiquido == null || valorLiquido.compareTo(valorTotal) == 0) {
                    valorLiquido = valorTotal.multiply(BigDecimal.valueOf(0.85)).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }
        if (valorLiquido == null) {
            valorLiquido = valorTotal;
        }

        InvestimentoLancamento lancamento = InvestimentoLancamento.builder()
                .ativo(ativo)
                .tipoOperacao(dto.getTipoOperacao())
                .data(dto.getData())
                .quantidade(quantidade)
                .precoUnitario(precoUnit)
                .custos(custos)
                .valorTotal(valorTotal)
                .valorLiquido(valorLiquido)
                .dataVencimento(dto.getDataVencimento())
                .indexador(dto.getIndexador())
                .taxa(dto.getTaxa())
                .tipoProvento(dto.getTipoProvento())
                .build();

        if (dto.getTipoAtivo() == TipoAtivo.RENDA_FIXA || dto.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO) {
            ativo.setDataVencimento(dto.getDataVencimento());
            ativo.setIndexador(dto.getIndexador());
            ativo.setTaxa(dto.getTaxa());
        }

        lancamento = lancamentoRepository.save(lancamento);

        Categoria categoriaFinanceira = Categoria.TRANSFERENCIA;
        if (dto.getTipoOperacao() == TipoOperacao.DIVIDENDO || dto.getTipoOperacao() == TipoOperacao.VENDA) {
            categoriaFinanceira = Categoria.RECEITA;
        }

        // Gera lançamento financeiro cruzado
        Lancamento fin = Lancamento.builder()
                .descricao((dto.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Dividendo: " : 
                            dto.getTipoOperacao() == TipoOperacao.COMPRA ? "Compra de Ativo: " : "Venda de Ativo: ") 
                           + ativo.getTicker())
                .categoria(categoriaFinanceira)
                .subcategoria(dto.getTipoOperacao() == TipoOperacao.COMPRA ? mapearSubcategoriaAporte(dto.getTipoAtivo()) :
                              dto.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Proventos" : "Resgate de Investimentos")
                .valor(dto.getTipoOperacao() == TipoOperacao.DIVIDENDO ? valorLiquido : valorTotal)
                .mes(dto.getData().getMonthValue())
                .ano(dto.getData().getYear())
                .dia(dto.getData().getDayOfMonth())
                .build();
        
        fin = financeiroRepository.save(fin);
        lancamento.setLancamentoFinanceiroId(fin.getId());
        lancamentoRepository.save(lancamento);

        // Recalcula o ativo
        recalcularAtivo(ativo, dto.getTipoOperacao(), quantidade, precoUnit, valorTotal, valorLiquido);

        return toLancamentoDTO(lancamento);
    }

    @Transactional
    public InvestimentoLancamentoDTO atualizarLancamento(Long id, InvestimentoLancamentoDTO dto) {
        InvestimentoLancamento lancamento = lancamentoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lançamento não encontrado: " + id));

        if (dto.getQuantidade() != null) lancamento.setQuantidade(dto.getQuantidade());
        if (dto.getPrecoUnitario() != null) lancamento.setPrecoUnitario(dto.getPrecoUnitario());
        if (dto.getCustos() != null) lancamento.setCustos(dto.getCustos());
        if (dto.getValorTotal() != null) lancamento.setValorTotal(dto.getValorTotal());
        if (dto.getData() != null) lancamento.setData(dto.getData());
        if (dto.getDataVencimento() != null) lancamento.setDataVencimento(dto.getDataVencimento());
        if (dto.getIndexador() != null) lancamento.setIndexador(dto.getIndexador());
        if (dto.getTaxa() != null) lancamento.setTaxa(dto.getTaxa());
        if (dto.getTipoProvento() != null) lancamento.setTipoProvento(dto.getTipoProvento());

        // Processamento do Valor Líquido com tratamento para JSCP
        if (dto.getValorLiquido() != null) {
            BigDecimal valorLiquido = dto.getValorLiquido();
            if (lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO && "JSCP".equalsIgnoreCase(lancamento.getTipoProvento())) {
                if (valorLiquido.compareTo(lancamento.getValorTotal()) == 0) {
                    valorLiquido = lancamento.getValorTotal().multiply(BigDecimal.valueOf(0.85)).setScale(2, RoundingMode.HALF_UP);
                }
            }
            lancamento.setValorLiquido(valorLiquido);
        } else if (dto.getValorTotal() != null) {
            if (lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO && "JSCP".equalsIgnoreCase(lancamento.getTipoProvento())) {
                lancamento.setValorLiquido(dto.getValorTotal().multiply(BigDecimal.valueOf(0.85)).setScale(2, RoundingMode.HALF_UP));
            } else {
                lancamento.setValorLiquido(dto.getValorTotal());
            }
        }

        lancamentoRepository.save(lancamento);

        // Sincroniza o lançamento financeiro
        if (lancamento.getLancamentoFinanceiroId() != null) {
            financeiroRepository.findById(lancamento.getLancamentoFinanceiroId()).ifPresent(fin -> {
                fin.setDescricao((lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Dividendo: " : 
                                  lancamento.getTipoOperacao() == TipoOperacao.COMPRA ? "Compra de Ativo: " : "Venda de Ativo: ") 
                                 + lancamento.getAtivo().getTicker());
                
                Categoria catFin = Categoria.TRANSFERENCIA;
                if (lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO || lancamento.getTipoOperacao() == TipoOperacao.VENDA) {
                    catFin = Categoria.RECEITA;
                }
                fin.setCategoria(catFin);
                
                fin.setSubcategoria(lancamento.getTipoOperacao() == TipoOperacao.COMPRA ? mapearSubcategoriaAporte(lancamento.getAtivo().getTipoAtivo()) :
                                    lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Proventos" : "Resgate de Investimentos");
                fin.setValor(lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO ? lancamento.getValorLiquido() : lancamento.getValorTotal());
                fin.setMes(lancamento.getData().getMonthValue());
                fin.setAno(lancamento.getData().getYear());
                fin.setDia(lancamento.getData().getDayOfMonth());
                financeiroRepository.save(fin);
            });
        } else {
            // Caso seja um lançamento antigo e ainda não tenha sido sincronizado, cria um novo
            Categoria catFin = Categoria.TRANSFERENCIA;
            if (lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO || lancamento.getTipoOperacao() == TipoOperacao.VENDA) {
                catFin = Categoria.RECEITA;
            }
            
            Lancamento fin = Lancamento.builder()
                    .descricao((lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Dividendo: " : 
                                lancamento.getTipoOperacao() == TipoOperacao.COMPRA ? "Compra de Ativo: " : "Venda de Ativo: ") 
                               + lancamento.getAtivo().getTicker())
                    .categoria(catFin)
                    .subcategoria(lancamento.getTipoOperacao() == TipoOperacao.COMPRA ? mapearSubcategoriaAporte(lancamento.getAtivo().getTipoAtivo()) :
                                  lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Proventos" : "Resgate de Investimentos")
                    .valor(lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO ? lancamento.getValorLiquido() : lancamento.getValorTotal())
                    .mes(lancamento.getData().getMonthValue())
                    .ano(lancamento.getData().getYear())
                    .dia(lancamento.getData().getDayOfMonth())
                    .build();
            fin = financeiroRepository.save(fin);
            lancamento.setLancamentoFinanceiroId(fin.getId());
            lancamentoRepository.save(lancamento);
        }

        // Recalcula o ativo completo
        if (CryptoEtfUtils.isCryptoEtf(lancamento.getAtivo().getTicker())) {
            Ativo ativo = lancamento.getAtivo();
            ativo.setTipoAtivo(TipoAtivo.CRIPTO);
            if (ativo.getCategoriaTatica() == null || ativo.getCategoriaTatica() == CategoriaTatica.GLOBAL) {
                ativo.setCategoriaTatica(CategoriaTatica.CRESCIMENTO);
            }
            ativoRepository.save(ativo);
        } else if (dto.getTipoAtivo() != null && lancamento.getAtivo().getTipoAtivo() != dto.getTipoAtivo()) {
            Ativo ativo = lancamento.getAtivo();
            ativo.setTipoAtivo(dto.getTipoAtivo());
            ativoRepository.save(ativo);
        }

        if (dto.getCategoriaTatica() != null) {
            Ativo ativo = lancamento.getAtivo();
            ativo.setCategoriaTatica(dto.getCategoriaTatica());
            ativoRepository.save(ativo);
        }

        recalcularAtivoCompleto(lancamento.getAtivo());

        return toLancamentoDTO(lancamento);
    }

    @Transactional
    public void excluirLancamento(Long lancamentoId) {
        InvestimentoLancamento lancamento = lancamentoRepository.findById(lancamentoId)
                .orElseThrow(() -> new NoSuchElementException("Lançamento não encontrado: " + lancamentoId));

        Ativo ativo = lancamento.getAtivo();

        if (lancamento.getLancamentoFinanceiroId() != null) {
            financeiroRepository.deleteById(lancamento.getLancamentoFinanceiroId());
        }

        lancamentoRepository.delete(lancamento);

        // Recalcula totalmente a partir do histórico
        recalcularAtivoCompleto(ativo);
    }

    // ─── ATIVOS ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AtivoDTO> listarAtivos(TipoAtivo tipo) {
        List<Ativo> ativos;
        if (tipo != null) {
            ativos = ativoRepository.findByTipoAtivoAndAtivoTrueOrderByTickerAsc(tipo);
        } else {
            ativos = ativoRepository.findByAtivoTrueOrderByTipoAtivoAscTickerAsc();
        }

        String token = aiConfigRepository.findFirstByOrderByIdDesc()
                .map(AiConfig::getBrapiToken)
                .orElse(null);
        BigDecimal selicRate = cotacaoService.getSelicRate(token);
        BigDecimal ipcaRate = cotacaoService.getIpcaRate(token);

        List<AtivoDTO> dtos = ativos.stream()
                .map(a -> toAtivoDTO(a, BigDecimal.ZERO, selicRate, ipcaRate))
                .collect(Collectors.toList());

        BigDecimal patrimonioTotal = dtos.stream()
                .map(AtivoDTO::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (patrimonioTotal.compareTo(BigDecimal.ZERO) > 0) {
            for (AtivoDTO dto : dtos) {
                BigDecimal percent = dto.getValorTotal().divide(patrimonioTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                dto.setPercentCarteira(percent);
            }
        }

        return dtos;
    }

    @Transactional
    public AtivoDTO atualizarPreco(Long ativoId, BigDecimal novoPreco) {
        Ativo ativo = ativoRepository.findById(ativoId)
                .orElseThrow(() -> new NoSuchElementException("Ativo não encontrado: " + ativoId));
        ativo.setPrecoAtual(novoPreco);
        ativoRepository.save(ativo);

        String token = aiConfigRepository.findFirstByOrderByIdDesc()
                .map(AiConfig::getBrapiToken)
                .orElse(null);
        BigDecimal selicRate = cotacaoService.getSelicRate(token);
        BigDecimal ipcaRate = cotacaoService.getIpcaRate(token);

        BigDecimal patrimonioTotal = calcularPatrimonioTotal(selicRate, ipcaRate);
        return toAtivoDTO(ativo, patrimonioTotal, selicRate, ipcaRate);
    }

    @Transactional
    public AtivoDTO atualizarAtivo(Long ativoId, AtivoDTO dto) {
        Ativo ativo = ativoRepository.findById(ativoId)
                .orElseThrow(() -> new NoSuchElementException("Ativo não encontrado: " + ativoId));

        if (dto.getNome() != null) ativo.setNome(dto.getNome());
        if (dto.getMetaPercent() != null) ativo.setMetaPercent(dto.getMetaPercent());
        if (dto.getPrecoAtual() != null) ativo.setPrecoAtual(dto.getPrecoAtual());
        if (dto.getDataVencimento() != null) ativo.setDataVencimento(dto.getDataVencimento());
        if (dto.getIndexador() != null) ativo.setIndexador(dto.getIndexador());
        if (dto.getTaxa() != null) ativo.setTaxa(dto.getTaxa());
        if (dto.getCategoriaTatica() != null) ativo.setCategoriaTatica(dto.getCategoriaTatica());
        ativo.setCiclico(dto.isCiclico());
        ativo.setEstrutural(dto.isEstrutural());
        if (CryptoEtfUtils.isCryptoEtf(ativo.getTicker())) {
            ativo.setTipoAtivo(TipoAtivo.CRIPTO);
            if (ativo.getCategoriaTatica() == null || ativo.getCategoriaTatica() == CategoriaTatica.GLOBAL) {
                ativo.setCategoriaTatica(CategoriaTatica.CRESCIMENTO);
            }
        }

        ativoRepository.save(ativo);

        String token = aiConfigRepository.findFirstByOrderByIdDesc()
                .map(AiConfig::getBrapiToken)
                .orElse(null);
        BigDecimal selicRate = cotacaoService.getSelicRate(token);
        BigDecimal ipcaRate = cotacaoService.getIpcaRate(token);

        BigDecimal patrimonioTotal = calcularPatrimonioTotal(selicRate, ipcaRate);
        return toAtivoDTO(ativo, patrimonioTotal, selicRate, ipcaRate);
    }

    // ─── DASHBOARD ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InvestimentoDashboardDTO calcularDashboard() {
        return investimentoCalculoService.calcularDashboard();
    }


    // ─── LANÇAMENTOS ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InvestimentoLancamentoDTO> listarLancamentos(Long ativoId) {
        List<InvestimentoLancamento> lancamentos;
        if (ativoId != null) {
            lancamentos = lancamentoRepository.findByAtivoIdOrderByDataDesc(ativoId);
        } else {
            lancamentos = lancamentoRepository.findAllByOrderByDataDesc();
        }
        return lancamentos.stream().map(this::toLancamentoDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listarProventosHistorico() {
        List<InvestimentoLancamento> proventos = lancamentoRepository.findAllByOrderByDataDesc().stream()
                .filter(l -> l.getTipoOperacao() == TipoOperacao.DIVIDENDO)
                .sorted(Comparator.comparing(InvestimentoLancamento::getData).thenComparing(InvestimentoLancamento::getId))
                .toList();

        List<InvestimentoLancamentoDTO> dtos = proventos.stream().map(this::toLancamentoDTO).collect(Collectors.toList());

        // Agrega por ano → mês → tipo (para tooltips por célula)
        // porAnoMesTipo[ano][mes][tipo] = valor
        Map<Integer, Map<Integer, Map<String, BigDecimal>>> porAnoMesTipo = new TreeMap<>(Comparator.reverseOrder());
        Map<Integer, Map<Integer, BigDecimal>> porAnoMes = new TreeMap<>(Comparator.reverseOrder());
        Map<Integer, BigDecimal> totalPorAno = new TreeMap<>(Comparator.reverseOrder());
        BigDecimal totalGeral = BigDecimal.ZERO;
        Map<String, BigDecimal> totalPorTipo = new LinkedHashMap<>();

        for (InvestimentoLancamento l : proventos) {
            int ano = l.getData().getYear();
            int mes = l.getData().getMonthValue();
            BigDecimal liq = l.getValorLiquido() != null ? l.getValorLiquido() : l.getValorTotal();
            String tipo = l.getAtivo().getTipoAtivo() != null ? l.getAtivo().getTipoAtivo().name() : "OUTRO";

            porAnoMes.computeIfAbsent(ano, k -> new TreeMap<>()).merge(mes, liq, BigDecimal::add);
            porAnoMesTipo.computeIfAbsent(ano, k -> new TreeMap<>())
                         .computeIfAbsent(mes, k -> new LinkedHashMap<>())
                         .merge(tipo, liq, BigDecimal::add);
            totalPorAno.merge(ano, liq, BigDecimal::add);
            totalGeral = totalGeral.add(liq);
            totalPorTipo.merge(tipo, liq, BigDecimal::add);
        }

        // Converte para estrutura serializável com breakdown por tipo por célula
        List<Map<String, Object>> anoRows = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, BigDecimal>> entry : porAnoMes.entrySet()) {
            int ano = entry.getKey();
            Map<Integer, BigDecimal> mesMapa = entry.getValue();
            Map<Integer, Map<String, BigDecimal>> mesTipoMapa = porAnoMesTipo.getOrDefault(ano, Map.of());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ano", ano);

            // Total = soma dos meses com valor (não divide por 12)
            BigDecimal totalAno = totalPorAno.getOrDefault(ano, BigDecimal.ZERO);
            // Média = total / número de meses com lançamentos
            long mesesComDados = mesMapa.values().stream().filter(v -> v.compareTo(BigDecimal.ZERO) > 0).count();
            BigDecimal media = mesesComDados > 0
                    ? totalAno.divide(BigDecimal.valueOf(mesesComDados), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            row.put("media", media);
            row.put("total", totalAno);

            for (int m = 1; m <= 12; m++) {
                row.put("m" + m, mesMapa.getOrDefault(m, BigDecimal.ZERO));
                // Breakdown por tipo para tooltip
                Map<String, BigDecimal> tipoBreakdown = mesTipoMapa.getOrDefault(m, Map.of());
                row.put("m" + m + "Tipo", tipoBreakdown);
            }
            anoRows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", totalGeral);
        result.put("lancamentos", dtos);
        result.put("porAno", anoRows);
        result.put("porTipo", totalPorTipo);
        return result;
    }

    // ─── RECÁLCULOS INTERNOS ────────────────────────────────────────────────

    private void recalcularAtivo(Ativo ativo, TipoOperacao operacao, BigDecimal qtd, BigDecimal preco, BigDecimal valorTotal, BigDecimal valorLiquido) {
        switch (operacao) {
            case COMPRA -> {
                BigDecimal qtdAnterior = ativo.getQuantidade();
                BigDecimal pmAnterior = ativo.getPrecoMedio();
                BigDecimal custoAnterior = qtdAnterior.multiply(pmAnterior);
                BigDecimal custoNovo = qtd.multiply(preco);
                BigDecimal novaQtd = qtdAnterior.add(qtd);

                BigDecimal novoPM = BigDecimal.ZERO;
                if (novaQtd.compareTo(BigDecimal.ZERO) > 0) {
                    novoPM = custoAnterior.add(custoNovo).divide(novaQtd, 2, RoundingMode.HALF_UP);
                }

                ativo.setQuantidade(novaQtd);
                ativo.setPrecoMedio(novoPM);
                // Se é a primeira compra, preço atual = preço da compra
                if (ativo.getPrecoAtual().compareTo(BigDecimal.ZERO) == 0) {
                    ativo.setPrecoAtual(preco);
                }
                ativo.setAtivo(true);
            }
            case VENDA -> {
                BigDecimal novaQtd = ativo.getQuantidade().subtract(qtd);
                if (novaQtd.compareTo(BigDecimal.ZERO) <= 0) {
                    novaQtd = BigDecimal.ZERO;
                    ativo.setAtivo(false);
                }
                ativo.setQuantidade(novaQtd);
                // PM não muda na venda
            }
            case DIVIDENDO -> {
                BigDecimal liq = valorLiquido != null ? valorLiquido : valorTotal;
                ativo.setDividendosTotal(ativo.getDividendosTotal().add(liq));
            }
        }

        ativoRepository.save(ativo);
    }

    /**
     * Recalcula completamente um ativo a partir do histórico de lançamentos.
     * Usado quando um lançamento é excluído.
     */
    private void recalcularAtivoCompleto(Ativo ativo) {
        List<InvestimentoLancamento> lancamentos = lancamentoRepository.findByAtivoIdOrderByDataDesc(ativo.getId());

        // Reseta
        ativo.setQuantidade(BigDecimal.ZERO);
        ativo.setPrecoMedio(BigDecimal.ZERO);
        ativo.setDividendosTotal(BigDecimal.ZERO);

        // Reprocessa todos os lançamentos em ordem cronológica
        List<InvestimentoLancamento> ordenados = lancamentos.stream()
                .sorted(Comparator.comparing(InvestimentoLancamento::getData)
                        .thenComparing(InvestimentoLancamento::getId))
                .toList();

        for (InvestimentoLancamento l : ordenados) {
            recalcularAtivo(ativo, l.getTipoOperacao(), l.getQuantidade(), l.getPrecoUnitario(), l.getValorTotal(), l.getValorLiquido());
        }

        if (ativo.getQuantidade().compareTo(BigDecimal.ZERO) <= 0) {
            ativo.setAtivo(false);
        }

        if (ativo.getTipoAtivo() == TipoAtivo.RENDA_FIXA || ativo.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO) {
            ativo.setDataVencimento(null);
            ativo.setIndexador(null);
            ativo.setTaxa(null);
            for (int i = ordenados.size() - 1; i >= 0; i--) {
                InvestimentoLancamento l = ordenados.get(i);
                if (l.getIndexador() != null || l.getDataVencimento() != null) {
                    ativo.setDataVencimento(l.getDataVencimento());
                    ativo.setIndexador(l.getIndexador());
                    ativo.setTaxa(l.getTaxa());
                    break;
                }
            }
        }

        ativoRepository.save(ativo);
    }

    private BigDecimal calcularPatrimonioTotal(BigDecimal selicRate, BigDecimal ipcaRate) {
        return investimentoCalculoService.calcularPatrimonioTotal(selicRate, ipcaRate);
    }

    private AtivoDTO toAtivoDTO(Ativo a, BigDecimal patrimonioTotal, BigDecimal selicRate, BigDecimal ipcaRate) {
        return investimentoCalculoService.toAtivoDTO(a, patrimonioTotal, selicRate, ipcaRate);
    }

    private InvestimentoLancamentoDTO toLancamentoDTO(InvestimentoLancamento l) {
        String tpNorm = l.getTipoOperacao() == TipoOperacao.DIVIDENDO
                ? AiService.normalizarTipoProvento(l.getTipoProvento())
                : l.getTipoProvento();

        Ativo a = l.getAtivo();
        CategoriaTatica cat = a.getCategoriaTatica() != null && a.getCategoriaTatica() != CategoriaTatica.RENDA ? a.getCategoriaTatica() : CategoriaTaticaUtils.inferirCategoriaTatica(a);

        return InvestimentoLancamentoDTO.builder()
                .id(l.getId())
                .ativoId(l.getAtivo().getId())
                .ticker(l.getAtivo().getTicker())
                .tipoAtivo(l.getAtivo().getTipoAtivo())
                .categoriaTatica(cat)
                .tipoOperacao(l.getTipoOperacao())
                .data(l.getData())
                .quantidade(l.getQuantidade())
                .precoUnitario(l.getPrecoUnitario())
                .custos(l.getCustos())
                .valorTotal(l.getValorTotal())
                .valorLiquido(l.getValorLiquido() != null ? l.getValorLiquido() : l.getValorTotal())
                .dataVencimento(l.getDataVencimento())
                .indexador(l.getIndexador())
                .taxa(l.getTaxa())
                .tipoProvento(tpNorm)
                .build();
    }


    private String mapearSubcategoriaAporte(TipoAtivo tipo) {
        if (tipo == null) return "Investimentos";
        switch (tipo) {
            case ACAO: return "Ações";
            case FII: return "FIIs";
            case RENDA_FIXA: return "Renda Fixa";
            case ETF: return "ETFs";
            case TESOURO_DIRETO: return "Tesouro Direto";
            case CRIPTO: return "Criptomoedas";
            default: return "Investimentos";
        }
    }
}