package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.dto.AtivoDTO;
import br.com.lesnik.mytwocents.dto.InvestimentoDashboardDTO;
import br.com.lesnik.mytwocents.dto.InvestimentoLancamentoDTO;
import br.com.lesnik.mytwocents.model.*;
import br.com.lesnik.mytwocents.repository.AtivoRepository;
import br.com.lesnik.mytwocents.repository.InvestimentoLancamentoRepository;
import br.com.lesnik.mytwocents.repository.LancamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvestimentoService {

    private final AtivoRepository ativoRepository;
    private final InvestimentoLancamentoRepository lancamentoRepository;
    private final LancamentoRepository financeiroRepository;
    private final CotacaoService cotacaoService;
    private final br.com.lesnik.mytwocents.repository.AiConfigRepository aiConfigRepository;

    // ─── CRUD DE LANÇAMENTOS ─────────────────────────────────────────────────

    @Transactional
    public InvestimentoLancamentoDTO criarLancamento(InvestimentoLancamentoDTO dto) {
        // Busca ou cria o ativo
        Ativo ativo = ativoRepository.findByTickerIgnoreCase(dto.getTicker())
                .orElseGet(() -> {
                    Ativo novo = Ativo.builder()
                            .ticker(dto.getTicker().toUpperCase().trim())
                            .tipoAtivo(dto.getTipoAtivo())
                            .quantidade(BigDecimal.ZERO)
                            .precoMedio(BigDecimal.ZERO)
                            .precoAtual(BigDecimal.ZERO)
                            .dividendosTotal(BigDecimal.ZERO)
                            .ativo(true)
                            .build();
                    return ativoRepository.save(novo);
                });

        BigDecimal quantidade = dto.getQuantidade() != null ? dto.getQuantidade() : BigDecimal.ZERO;
        BigDecimal precoUnit = dto.getPrecoUnitario() != null ? dto.getPrecoUnitario() : BigDecimal.ZERO;
        BigDecimal custos = dto.getCustos() != null ? dto.getCustos() : BigDecimal.ZERO;

        BigDecimal valorTotal;
        if (dto.getTipoOperacao() == TipoOperacao.DIVIDENDO) {
            // Para dividendo, valorTotal = precoUnitario (o valor total recebido)
            valorTotal = precoUnit.add(custos);
        } else {
            valorTotal = quantidade.multiply(precoUnit).add(custos);
        }

        InvestimentoLancamento lancamento = InvestimentoLancamento.builder()
                .ativo(ativo)
                .tipoOperacao(dto.getTipoOperacao())
                .data(dto.getData())
                .quantidade(quantidade)
                .precoUnitario(precoUnit)
                .custos(custos)
                .valorTotal(valorTotal)
                .dataVencimento(dto.getDataVencimento())
                .indexador(dto.getIndexador())
                .taxa(dto.getTaxa())
                .build();

        if (dto.getTipoAtivo() == TipoAtivo.RENDA_FIXA || dto.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO) {
            ativo.setDataVencimento(dto.getDataVencimento());
            ativo.setIndexador(dto.getIndexador());
            ativo.setTaxa(dto.getTaxa());
        }

        lancamento = lancamentoRepository.save(lancamento);

        // Gera lançamento financeiro cruzado
        Lancamento fin = Lancamento.builder()
                .descricao((dto.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Dividendo: " : 
                            dto.getTipoOperacao() == TipoOperacao.COMPRA ? "Compra de Ativo: " : "Venda de Ativo: ") 
                           + ativo.getTicker())
                .categoria(dto.getTipoOperacao() == TipoOperacao.COMPRA ? Categoria.GASTO : Categoria.RECEITA)
                .subcategoria("Investimentos")
                .valor(valorTotal)
                .mes(dto.getData().getMonthValue())
                .ano(dto.getData().getYear())
                .dia(dto.getData().getDayOfMonth())
                .build();
        
        fin = financeiroRepository.save(fin);
        lancamento.setLancamentoFinanceiroId(fin.getId());
        lancamentoRepository.save(lancamento);

        // Recalcula o ativo
        recalcularAtivo(ativo, dto.getTipoOperacao(), quantidade, precoUnit, valorTotal);

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

        lancamentoRepository.save(lancamento);

        // Sincroniza o lançamento financeiro
        if (lancamento.getLancamentoFinanceiroId() != null) {
            financeiroRepository.findById(lancamento.getLancamentoFinanceiroId()).ifPresent(fin -> {
                fin.setValor(lancamento.getValorTotal());
                fin.setMes(lancamento.getData().getMonthValue());
                fin.setAno(lancamento.getData().getYear());
                fin.setDia(lancamento.getData().getDayOfMonth());
                financeiroRepository.save(fin);
            });
        } else {
            // Caso seja um lançamento antigo e ainda não tenha sido sincronizado, cria um novo
            Lancamento fin = Lancamento.builder()
                    .descricao((lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Dividendo: " : 
                                lancamento.getTipoOperacao() == TipoOperacao.COMPRA ? "Compra de Ativo: " : "Venda de Ativo: ") 
                               + lancamento.getAtivo().getTicker())
                    .categoria(lancamento.getTipoOperacao() == TipoOperacao.COMPRA ? Categoria.GASTO : Categoria.RECEITA)
                    .subcategoria("Investimentos")
                    .valor(lancamento.getValorTotal())
                    .mes(lancamento.getData().getMonthValue())
                    .ano(lancamento.getData().getYear())
                    .dia(lancamento.getData().getDayOfMonth())
                    .build();
            fin = financeiroRepository.save(fin);
            lancamento.setLancamentoFinanceiroId(fin.getId());
            lancamentoRepository.save(lancamento);
        }

        // Recalcula o ativo completo
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
        List<Ativo> ativos = ativoRepository.findByAtivoTrueOrderByTipoAtivoAscTickerAsc();

        String token = aiConfigRepository.findFirstByOrderByIdDesc()
                .map(AiConfig::getBrapiToken)
                .orElse(null);
        BigDecimal selicRate = cotacaoService.getSelicRate(token);
        BigDecimal ipcaRate = cotacaoService.getIpcaRate(token);

        List<AtivoDTO> dtos = ativos.stream()
                .map(a -> toAtivoDTO(a, BigDecimal.ZERO, selicRate, ipcaRate))
                .toList();

        BigDecimal patrimonioTotal = dtos.stream()
                .map(AtivoDTO::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorInvestido = BigDecimal.ZERO;
        BigDecimal dividendosTotal = BigDecimal.ZERO;

        Map<TipoAtivo, BigDecimal> distribuicao = new LinkedHashMap<>();
        Map<TipoAtivo, List<AtivoDTO>> ativosPorTipo = new LinkedHashMap<>();
        Map<TipoAtivo, BigDecimal> investidoPorTipo = new LinkedHashMap<>();
        Map<TipoAtivo, BigDecimal> dividendosPorTipo = new LinkedHashMap<>();

        for (int i = 0; i < ativos.size(); i++) {
            Ativo a = ativos.get(i);
            AtivoDTO dto = dtos.get(i);

            if (patrimonioTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percent = dto.getValorTotal().divide(patrimonioTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                dto.setPercentCarteira(percent);
            }

            BigDecimal valorAtivo = dto.getValorTotal();
            BigDecimal custoAtivo = a.getQuantidade().multiply(a.getPrecoMedio());
            
            valorInvestido = valorInvestido.add(custoAtivo);
            dividendosTotal = dividendosTotal.add(a.getDividendosTotal());

            distribuicao.merge(a.getTipoAtivo(), valorAtivo, BigDecimal::add);
            investidoPorTipo.merge(a.getTipoAtivo(), custoAtivo, BigDecimal::add);
            dividendosPorTipo.merge(a.getTipoAtivo(), a.getDividendosTotal(), BigDecimal::add);

            ativosPorTipo.computeIfAbsent(a.getTipoAtivo(), k -> new ArrayList<>()).add(dto);
        }

        // Resumo por tipo
        Map<TipoAtivo, InvestimentoDashboardDTO.TipoResumo> resumoPorTipo = new LinkedHashMap<>();
        for (TipoAtivo tipo : distribuicao.keySet()) {
            BigDecimal valorTipo = distribuicao.getOrDefault(tipo, BigDecimal.ZERO);
            BigDecimal investTipo = investidoPorTipo.getOrDefault(tipo, BigDecimal.ZERO);
            List<AtivoDTO> ativosTipo = ativosPorTipo.getOrDefault(tipo, List.of());

            BigDecimal variacaoTipo = BigDecimal.ZERO;
            if (investTipo.compareTo(BigDecimal.ZERO) > 0) {
                variacaoTipo = valorTipo.subtract(investTipo)
                        .divide(investTipo, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            BigDecimal percentCarteira = BigDecimal.ZERO;
            if (patrimonioTotal.compareTo(BigDecimal.ZERO) > 0) {
                percentCarteira = valorTipo.divide(patrimonioTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            // Meta média do tipo
            BigDecimal metaMedia = ativosTipo.stream()
                    .map(AtivoDTO::getMetaPercent)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            resumoPorTipo.put(tipo, new InvestimentoDashboardDTO.TipoResumo(
                    ativosTipo.size(), valorTipo, variacaoTipo, percentCarteira, metaMedia
            ));
        }

        BigDecimal lucroTotal = patrimonioTotal.subtract(valorInvestido);
        BigDecimal variacaoPercent = BigDecimal.ZERO;
        if (valorInvestido.compareTo(BigDecimal.ZERO) > 0) {
            variacaoPercent = lucroTotal.divide(valorInvestido, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // ─── LÓGICA DE EVOLUÇÃO 12 MESES ─────────────────────────────────────────
        List<String> labels12m = new ArrayList<>();
        List<BigDecimal> evolucaoInvestido12m = new ArrayList<>();
        List<BigDecimal> evolucaoPatrimonio12m = new ArrayList<>();
        List<BigDecimal> evolucaoDividendos12m = new ArrayList<>();
        
        java.time.YearMonth currentMonth = java.time.YearMonth.now();
        List<java.time.YearMonth> last12Months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            last12Months.add(currentMonth.minusMonths(i));
        }
        
        String[] nomesMeses = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};
        for (java.time.YearMonth ym : last12Months) {
            labels12m.add(nomesMeses[ym.getMonthValue() - 1] + "/" + (ym.getYear() % 100));
        }

        List<InvestimentoLancamento> todosLancamentos = lancamentoRepository.findAllByOrderByDataDesc().stream()
                .sorted(Comparator.comparing(InvestimentoLancamento::getData).thenComparing(InvestimentoLancamento::getId))
                .toList();

        // Para calcular o estado no fim de cada mês, precisamos processar cronologicamente
        // Estado atual simulado da carteira: ativoId -> {qtd, pm}
        class EstadoAtivo {
            BigDecimal qtd = BigDecimal.ZERO;
            BigDecimal pm = BigDecimal.ZERO;
        }
        
        Map<Long, EstadoAtivo> carteiraSimulada = new HashMap<>();
        int lancamentoIdx = 0;

        for (java.time.YearMonth ym : last12Months) {
            java.time.LocalDate endOfMonth = ym.atEndOfMonth();
            BigDecimal dividendosNoMes = BigDecimal.ZERO;
            
            // Processa lançamentos até o fim deste mês
            while (lancamentoIdx < todosLancamentos.size() && 
                   !todosLancamentos.get(lancamentoIdx).getData().isAfter(endOfMonth)) {
                InvestimentoLancamento l = todosLancamentos.get(lancamentoIdx);
                EstadoAtivo estado = carteiraSimulada.computeIfAbsent(l.getAtivo().getId(), k -> new EstadoAtivo());
                
                if (l.getTipoOperacao() == TipoOperacao.COMPRA) {
                    BigDecimal custoAnterior = estado.qtd.multiply(estado.pm);
                    BigDecimal custoNovo = l.getQuantidade().multiply(l.getPrecoUnitario());
                    BigDecimal novaQtd = estado.qtd.add(l.getQuantidade());
                    if (novaQtd.compareTo(BigDecimal.ZERO) > 0) {
                        estado.pm = custoAnterior.add(custoNovo).divide(novaQtd, 2, RoundingMode.HALF_UP);
                    }
                    estado.qtd = novaQtd;
                } else if (l.getTipoOperacao() == TipoOperacao.VENDA) {
                    estado.qtd = estado.qtd.subtract(l.getQuantidade());
                    if (estado.qtd.compareTo(BigDecimal.ZERO) < 0) estado.qtd = BigDecimal.ZERO;
                    // PM não muda na venda
                } else if (l.getTipoOperacao() == TipoOperacao.DIVIDENDO) {
                    // Adiciona aos dividendos apenas se for do mês exato que estamos iterando
                    if (java.time.YearMonth.from(l.getData()).equals(ym)) {
                        dividendosNoMes = dividendosNoMes.add(l.getValorTotal());
                    }
                }
                lancamentoIdx++;
            }
            
            // Fim do mês processado. Calcular o Patrimônio e Investido simulados
            BigDecimal investidoFimMes = BigDecimal.ZERO;
            BigDecimal patrimonioFimMes = BigDecimal.ZERO;
            
            for (Map.Entry<Long, EstadoAtivo> entry : carteiraSimulada.entrySet()) {
                EstadoAtivo estado = entry.getValue();
                if (estado.qtd.compareTo(BigDecimal.ZERO) > 0) {
                    investidoFimMes = investidoFimMes.add(estado.qtd.multiply(estado.pm));
                    
                    // Pega o preço atual do ativo (como aproximação)
                    BigDecimal precoAtual = ativos.stream()
                            .filter(a -> a.getId().equals(entry.getKey()))
                            .map(Ativo::getPrecoAtual)
                            .findFirst()
                            .orElse(BigDecimal.ZERO); // se não achar, usa 0 (mas sempre deve achar)
                            
                    patrimonioFimMes = patrimonioFimMes.add(estado.qtd.multiply(precoAtual));
                }
            }
            
            evolucaoInvestido12m.add(investidoFimMes);
            evolucaoPatrimonio12m.add(patrimonioFimMes);
            evolucaoDividendos12m.add(dividendosNoMes);
        }

        return InvestimentoDashboardDTO.builder()
                .patrimonioTotal(patrimonioTotal)
                .valorInvestido(valorInvestido)
                .lucroTotal(lucroTotal)
                .dividendosTotal(dividendosTotal)
                .variacaoPercent(variacaoPercent)
                .distribuicaoPorTipo(distribuicao)
                .ativosPorTipo(ativosPorTipo)
                .resumoPorTipo(resumoPorTipo)
                .evolucaoInvestido12m(evolucaoInvestido12m)
                .evolucaoPatrimonio12m(evolucaoPatrimonio12m)
                .evolucaoDividendos12m(evolucaoDividendos12m)
                .labels12m(labels12m)
                .build();
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

    // ─── RECÁLCULOS INTERNOS ────────────────────────────────────────────────

    private void recalcularAtivo(Ativo ativo, TipoOperacao operacao, BigDecimal qtd, BigDecimal preco, BigDecimal valorTotal) {
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
                ativo.setDividendosTotal(ativo.getDividendosTotal().add(valorTotal));
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
            recalcularAtivo(ativo, l.getTipoOperacao(), l.getQuantidade(), l.getPrecoUnitario(), l.getValorTotal());
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

    // ─── UTILITÁRIOS ────────────────────────────────────────────────────────

    private BigDecimal calcularPatrimonioTotal(BigDecimal selicRate, BigDecimal ipcaRate) {
        return ativoRepository.findByAtivoTrueOrderByTipoAtivoAscTickerAsc().stream()
                .map(a -> toAtivoDTO(a, BigDecimal.ZERO, selicRate, ipcaRate).getValorTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private AtivoDTO toAtivoDTO(Ativo a, BigDecimal patrimonioTotal, BigDecimal selicRate, BigDecimal ipcaRate) {
        BigDecimal valorTotal = a.getQuantidade().multiply(a.getPrecoAtual());
        BigDecimal rendimentoMensal = BigDecimal.ZERO;

        if ((a.getTipoAtivo() == TipoAtivo.RENDA_FIXA || a.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO)
                && a.getIndexador() != null && a.getTaxa() != null && a.getQuantidade().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taxaAnual = BigDecimal.ZERO;
            String index = a.getIndexador().toUpperCase().trim();
            BigDecimal taxaContratada = a.getTaxa();

            if (index.equals("CDI")) {
                BigDecimal cdi = selicRate.subtract(BigDecimal.valueOf(0.10));
                taxaAnual = taxaContratada.multiply(cdi).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            } else if (index.equals("SELIC")) {
                taxaAnual = selicRate.add(taxaContratada);
            } else if (index.equals("IPCA")) {
                taxaAnual = ipcaRate.add(taxaContratada);
            } else if (index.equals("PRE")) {
                taxaAnual = taxaContratada;
            }

            if (taxaAnual.compareTo(BigDecimal.ZERO) > 0) {
                List<InvestimentoLancamento> txs = lancamentoRepository.findByAtivoIdOrderByDataDesc(a.getId()).stream()
                        .sorted(Comparator.comparing(InvestimentoLancamento::getData)
                                .thenComparing(InvestimentoLancamento::getId))
                        .toList();

                if (!txs.isEmpty()) {
                    double annualRateDouble = taxaAnual.doubleValue() / 100.0;
                    double dailyRateDouble = Math.pow(1.0 + annualRateDouble, 1.0 / 365.0) - 1.0;
                    
                    double currentBalance = 0.0;
                    java.time.LocalDate lastDate = txs.get(0).getData();

                    for (InvestimentoLancamento tx : txs) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(lastDate, tx.getData());
                        if (days > 0) {
                            currentBalance = currentBalance * Math.pow(1.0 + dailyRateDouble, days);
                        }
                        lastDate = tx.getData();

                        if (tx.getTipoOperacao() == br.com.lesnik.mytwocents.model.TipoOperacao.COMPRA) {
                            currentBalance += tx.getValorTotal().doubleValue();
                        } else if (tx.getTipoOperacao() == br.com.lesnik.mytwocents.model.TipoOperacao.VENDA) {
                            currentBalance -= tx.getValorTotal().doubleValue();
                        }
                    }

                    long finalDays = java.time.temporal.ChronoUnit.DAYS.between(lastDate, java.time.LocalDate.now());
                    if (finalDays > 0) {
                        currentBalance = currentBalance * Math.pow(1.0 + dailyRateDouble, finalDays);
                    }

                    if (currentBalance < 0.0) {
                        currentBalance = 0.0;
                    }

                    valorTotal = BigDecimal.valueOf(currentBalance).setScale(2, RoundingMode.HALF_UP);
                }

                double annualRateDouble = taxaAnual.doubleValue() / 100.0;
                double monthlyRateDouble = Math.pow(1.0 + annualRateDouble, 1.0 / 12.0) - 1.0;
                BigDecimal taxaMensal = BigDecimal.valueOf(monthlyRateDouble);
                rendimentoMensal = valorTotal.multiply(taxaMensal).setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal variacao = BigDecimal.ZERO;
        BigDecimal lucro = BigDecimal.ZERO;

        BigDecimal custoTotal = a.getQuantidade().multiply(a.getPrecoMedio());
        if (custoTotal.compareTo(BigDecimal.ZERO) > 0) {
            variacao = valorTotal.subtract(custoTotal)
                    .divide(custoTotal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            lucro = valorTotal.subtract(custoTotal);
        } else if (a.getPrecoMedio().compareTo(BigDecimal.ZERO) > 0) {
            variacao = a.getPrecoAtual().subtract(a.getPrecoMedio())
                    .divide(a.getPrecoMedio(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            lucro = a.getPrecoAtual().subtract(a.getPrecoMedio()).multiply(a.getQuantidade());
        }

        BigDecimal percentCarteira = BigDecimal.ZERO;
        if (patrimonioTotal.compareTo(BigDecimal.ZERO) > 0) {
            percentCarteira = valorTotal.divide(patrimonioTotal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return AtivoDTO.builder()
                .id(a.getId())
                .ticker(a.getTicker())
                .nome(a.getNome())
                .tipoAtivo(a.getTipoAtivo())
                .quantidade(a.getQuantidade())
                .precoMedio(a.getPrecoMedio())
                .precoAtual(a.getPrecoAtual())
                .valorTotal(valorTotal)
                .variacao(variacao)
                .lucro(lucro)
                .percentCarteira(percentCarteira)
                .metaPercent(a.getMetaPercent())
                .dividendosTotal(a.getDividendosTotal())
                .ativo(a.isAtivo())
                .logoUrl(a.getLogoUrl())
                .sector(a.getSector())
                .longName(a.getLongName())
                .dataVencimento(a.getDataVencimento())
                .indexador(a.getIndexador())
                .taxa(a.getTaxa())
                .rendimentoMensal(rendimentoMensal)
                .build();
    }

    private InvestimentoLancamentoDTO toLancamentoDTO(InvestimentoLancamento l) {
        return InvestimentoLancamentoDTO.builder()
                .id(l.getId())
                .ativoId(l.getAtivo().getId())
                .ticker(l.getAtivo().getTicker())
                .tipoAtivo(l.getAtivo().getTipoAtivo())
                .tipoOperacao(l.getTipoOperacao())
                .data(l.getData())
                .quantidade(l.getQuantidade())
                .precoUnitario(l.getPrecoUnitario())
                .custos(l.getCustos())
                .valorTotal(l.getValorTotal())
                .dataVencimento(l.getDataVencimento())
                .indexador(l.getIndexador())
                .taxa(l.getTaxa())
                .build();
    }
}
