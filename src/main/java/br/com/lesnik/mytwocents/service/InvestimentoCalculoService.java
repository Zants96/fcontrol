package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.dto.AtivoDTO;
import br.com.lesnik.mytwocents.dto.InvestimentoDashboardDTO;
import br.com.lesnik.mytwocents.model.*;
import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import br.com.lesnik.mytwocents.repository.AtivoRepository;
import br.com.lesnik.mytwocents.repository.InvestimentoLancamentoRepository;
import br.com.lesnik.mytwocents.repository.PatrimonioHistoricoRepository;
import br.com.lesnik.mytwocents.util.CategoriaTaticaUtils;
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
public class InvestimentoCalculoService {

    private final AtivoRepository ativoRepository;
    private final InvestimentoLancamentoRepository lancamentoRepository;
    private final AiConfigRepository aiConfigRepository;
    private final CotacaoService cotacaoService;
    private final PatrimonioHistoricoRepository patrimonioHistoricoRepository;

    @Transactional(readOnly = true)
    public InvestimentoDashboardDTO calcularDashboard() {
        List<Ativo> ativos = ativoRepository.findByAtivoTrueOrderByTipoAtivoAscTickerAsc();

        String token = aiConfigRepository.findFirstByOrderByIdDesc()
                .map(AiConfig::getBrapiToken)
                .orElse(null);
        BigDecimal selicRate = cotacaoService.getSelicRate(token);
        BigDecimal ipcaRate = cotacaoService.getIpcaRate(token);

        List<AtivoDTO> dtos = new ArrayList<>();
        for (Ativo a : ativos) {
            if (a.getTipoAtivo() == TipoAtivo.RENDA_FIXA) {
                List<InvestimentoLancamento> allTxs = lancamentoRepository.findByAtivoIdOrderByDataDesc(a.getId()).stream()
                        .sorted(Comparator.comparing(InvestimentoLancamento::getData)
                                .thenComparing(InvestimentoLancamento::getId))
                        .toList();

                List<InvestimentoLancamento> compras = allTxs.stream()
                        .filter(t -> t.getTipoOperacao() == br.com.lesnik.mytwocents.model.TipoOperacao.COMPRA)
                        .toList();

                double totalVendas = allTxs.stream()
                        .filter(t -> t.getTipoOperacao() == br.com.lesnik.mytwocents.model.TipoOperacao.VENDA)
                        .mapToDouble(t -> t.getValorTotal().doubleValue())
                        .sum();

                double vendasRestantes = totalVendas;
                for (InvestimentoLancamento tx : compras) {
                    double valorOriginal = tx.getValorTotal().doubleValue();
                    double valorExibido = valorOriginal;
                    if (vendasRestantes > 0) {
                        if (valorOriginal <= vendasRestantes) {
                            vendasRestantes -= valorOriginal;
                            continue; // Totalmente vendido
                        } else {
                            valorExibido = valorOriginal - vendasRestantes;
                            vendasRestantes = 0;
                        }
                    }

                    BigDecimal valorTotal = calcularValorAtualLancamentoComValorInicial(tx, a, BigDecimal.valueOf(valorExibido), selicRate, ipcaRate);
                    
                    BigDecimal rendimentoMensal = BigDecimal.ZERO;
                    if (a.getTaxa() != null) {
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
                            double annualRateDouble = taxaAnual.doubleValue() / 100.0;
                            double monthlyRateDouble = Math.pow(1.0 + annualRateDouble, 1.0 / 12.0) - 1.0;
                            BigDecimal taxaMensal = BigDecimal.valueOf(monthlyRateDouble);
                            rendimentoMensal = valorTotal.multiply(taxaMensal).setScale(2, RoundingMode.HALF_UP);
                        }
                    }

                    BigDecimal variacao = BigDecimal.ZERO;
                    BigDecimal custoOrig = BigDecimal.valueOf(valorExibido);
                    if (custoOrig.compareTo(BigDecimal.ZERO) > 0) {
                        variacao = valorTotal.subtract(custoOrig)
                                .divide(custoOrig, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                    }

                    AtivoDTO launchDto = AtivoDTO.builder()
                            .id(a.getId())
                            .ticker(a.getTicker())
                            .nome(a.getNome())
                            .tipoAtivo(a.getTipoAtivo())
                            .quantidade(tx.getQuantidade())
                            .precoMedio(tx.getPrecoUnitario())
                            .precoAtual(tx.getPrecoUnitario())
                            .valorTotal(valorTotal)
                            .variacao(variacao)
                            .lucro(valorTotal.subtract(custoOrig))
                            .percentCarteira(BigDecimal.ZERO)
                            .metaPercent(a.getMetaPercent())
                            .dividendosTotal(BigDecimal.ZERO)
                            .ativo(a.isAtivo())
                            .logoUrl(a.getLogoUrl())
                            .sector(a.getSector())
                            .longName(a.getLongName())
                            .dataVencimento(tx.getDataVencimento() != null ? tx.getDataVencimento() : a.getDataVencimento())
                            .indexador(tx.getIndexador() != null ? tx.getIndexador() : a.getIndexador())
                            .taxa(tx.getTaxa() != null ? tx.getTaxa() : a.getTaxa())
                            .rendimentoMensal(rendimentoMensal)
                            .dataLancamento(tx.getData())
                            .dy(BigDecimal.ZERO)
                            .categoriaTatica(a.getCategoriaTatica() != null && a.getCategoriaTatica() != CategoriaTatica.RENDA ? a.getCategoriaTatica() : CategoriaTaticaUtils.inferirCategoriaTatica(a))
                            .build();

                    dtos.add(launchDto);
                }
            } else {
                dtos.add(toAtivoDTO(a, BigDecimal.ZERO, selicRate, ipcaRate));
            }
        }

        BigDecimal patrimonioTotal = dtos.stream()
                .map(AtivoDTO::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorInvestido = BigDecimal.ZERO;
        // Calcula dividendos recebidos do ano até o mês atual
        java.time.LocalDate hoje = java.time.LocalDate.now();
        BigDecimal dividendosTotal = lancamentoRepository.findAllByOrderByDataDesc().stream()
                .filter(l -> l.getTipoOperacao() == TipoOperacao.DIVIDENDO 
                        && l.getData().getYear() == hoje.getYear() 
                        && !l.getData().isAfter(hoje))
                .map(l -> l.getValorLiquido() != null ? l.getValorLiquido() : l.getValorTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<TipoAtivo, BigDecimal> distribuicao = new LinkedHashMap<>();
        Map<TipoAtivo, List<AtivoDTO>> ativosPorTipo = new LinkedHashMap<>();
        Map<TipoAtivo, BigDecimal> investidoPorTipo = new LinkedHashMap<>();
        Map<TipoAtivo, BigDecimal> dividendosPorTipo = new LinkedHashMap<>();

        for (AtivoDTO dto : dtos) {
            if (patrimonioTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percent = dto.getValorTotal().divide(patrimonioTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                dto.setPercentCarteira(percent);
            }

            BigDecimal valorAtivo = dto.getValorTotal();
            BigDecimal custoAtivo = dto.getQuantidade().multiply(dto.getPrecoMedio());
            
            valorInvestido = valorInvestido.add(custoAtivo);

            distribuicao.merge(dto.getTipoAtivo(), valorAtivo, BigDecimal::add);
            investidoPorTipo.merge(dto.getTipoAtivo(), custoAtivo, BigDecimal::add);
            dividendosPorTipo.merge(dto.getTipoAtivo(), dto.getDividendosTotal() != null ? dto.getDividendosTotal() : BigDecimal.ZERO, BigDecimal::add);

            ativosPorTipo.computeIfAbsent(dto.getTipoAtivo(), k -> new ArrayList<>()).add(dto);
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
        
        List<InvestimentoLancamento> todosLancamentos = lancamentoRepository.findAllByOrderByDataDesc().stream()
                .sorted(Comparator.comparing(InvestimentoLancamento::getData).thenComparing(InvestimentoLancamento::getId))
                .toList();

        java.time.YearMonth maxMonth = java.time.YearMonth.now();

        List<java.time.YearMonth> last12Months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            last12Months.add(maxMonth.minusMonths(i));
        }
        
        String[] nomesMeses = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};
        for (java.time.YearMonth ym : last12Months) {
            labels12m.add(nomesMeses[ym.getMonthValue() - 1] + "/" + (ym.getYear() % 100));
        }

        for (java.time.YearMonth ym : last12Months) {
            java.time.LocalDate endOfMonth = ym.atEndOfMonth();
            boolean isMesCorrente = ym.equals(maxMonth);
            
            // Dividendos no mês
            BigDecimal dividendosNoMes = todosLancamentos.stream()
                    .filter(l -> l.getTipoOperacao() == TipoOperacao.DIVIDENDO && java.time.YearMonth.from(l.getData()).equals(ym))
                    .map(InvestimentoLancamento::getValorTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Optional<PatrimonioHistorico> snapshotOpt = patrimonioHistoricoRepository.findByAnoAndMes(ym.getYear(), ym.getMonthValue());

            BigDecimal valorInvMes;
            BigDecimal valorPatMes;

            if (!isMesCorrente && snapshotOpt.isPresent()) {
                PatrimonioHistorico snap = snapshotOpt.get();
                valorInvMes = snap.getValorInvestido();
                valorPatMes = snap.getValorPatrimonio();
            } else {
                Map<String, BigDecimal> valores = calcularPatrimonioEInvestidoNaData(endOfMonth, ativos, todosLancamentos, selicRate, ipcaRate);
                valorInvMes = valores.get("investido");
                valorPatMes = valores.get("patrimonio");

                PatrimonioHistorico snap = snapshotOpt.orElseGet(() -> PatrimonioHistorico.builder()
                        .ano(ym.getYear())
                        .mes(ym.getMonthValue())
                        .build());
                snap.setValorInvestido(valorInvMes);
                snap.setValorPatrimonio(valorPatMes);
                snap.setValorLucro(valorPatMes.subtract(valorInvMes));
                snap.setValorDividendos(dividendosNoMes);
                try {
                    patrimonioHistoricoRepository.save(snap);
                } catch (Exception e) {
                    log.warn("Erro ao salvar snapshot de patrimônio histórico para {}/{}: {}", ym.getMonthValue(), ym.getYear(), e.getMessage());
                }
            }

            evolucaoInvestido12m.add(valorInvMes);
            evolucaoPatrimonio12m.add(valorPatMes);
            evolucaoDividendos12m.add(dividendosNoMes);
        }

        return InvestimentoDashboardDTO.builder()
                .patrimonioTotal(patrimonioTotal)
                .valorInvestido(valorInvestido)
                .lucroTotal(lucroTotal)
                .dividendosTotal(dividendosTotal)
                .variacaoPercent(variacaoPercent)
                .distribuicaoPorTipo(distribuicao)
                .distribuicaoIdealPorTipo(calcularDistribuicaoIdeal(patrimonioTotal))
                .ativosPorTipo(ativosPorTipo)
                .resumoPorTipo(resumoPorTipo)
                .evolucaoInvestido12m(evolucaoInvestido12m)
                .evolucaoPatrimonio12m(evolucaoPatrimonio12m)
                .evolucaoDividendos12m(evolucaoDividendos12m)
                .labels12m(labels12m)
                .build();
    }

    public BigDecimal calcularPatrimonioTotal(BigDecimal selicRate, BigDecimal ipcaRate) {
        return ativoRepository.findByAtivoTrueOrderByTipoAtivoAscTickerAsc().stream()
                .map(a -> toAtivoDTO(a, BigDecimal.ZERO, selicRate, ipcaRate).getValorTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public AtivoDTO toAtivoDTO(Ativo a, BigDecimal patrimonioTotal, BigDecimal selicRate, BigDecimal ipcaRate) {
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

        BigDecimal dy = BigDecimal.ZERO;
        if (a.getDividendYield() != null && a.getDividendYield().compareTo(BigDecimal.ZERO) > 0) {
            dy = a.getDividendYield();
        } else if ((a.getTipoAtivo() == TipoAtivo.ACAO || a.getTipoAtivo() == TipoAtivo.FII)
                && a.getPrecoAtual().compareTo(BigDecimal.ZERO) > 0
                && a.getQuantidade().compareTo(BigDecimal.ZERO) > 0) {
            java.time.LocalDate hoje = java.time.LocalDate.now();
            java.time.LocalDate dozeMesesAtras = hoje.minusMonths(12);

            BigDecimal dividendos12Meses = lancamentoRepository.findByAtivoIdOrderByDataDesc(a.getId()).stream()
                    .filter(l -> l.getTipoOperacao() == TipoOperacao.DIVIDENDO
                            && !l.getData().isBefore(dozeMesesAtras)
                            && !l.getData().isAfter(hoje))
                    .map(l -> l.getValorLiquido() != null ? l.getValorLiquido() : l.getValorTotal())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalPosicao = a.getQuantidade().multiply(a.getPrecoAtual());
            if (totalPosicao.compareTo(BigDecimal.ZERO) > 0) {
                dy = dividendos12Meses.multiply(BigDecimal.valueOf(100))
                        .divide(totalPosicao, 4, RoundingMode.HALF_UP);
            }
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
                .dy(dy)
                .categoriaTatica(a.getCategoriaTatica() != null && a.getCategoriaTatica() != CategoriaTatica.RENDA ? a.getCategoriaTatica() : CategoriaTaticaUtils.inferirCategoriaTatica(a))
                .ciclico(a.isCiclico())
                .estrutural(a.isEstrutural())
                .build();
    }

    private BigDecimal calcularValorAtualLancamentoComValorInicial(
            InvestimentoLancamento tx, Ativo a, BigDecimal valorInicial, BigDecimal selicRate, BigDecimal ipcaRate) {
        return calcularValorAtualLancamentoComValorInicialNaData(tx, a, valorInicial, java.time.LocalDate.now(), selicRate, ipcaRate);
    }

    private BigDecimal calcularValorAtualLancamentoComValorInicialNaData(
            InvestimentoLancamento tx, Ativo a, BigDecimal valorInicial, LocalDate targetDate, BigDecimal selicRate, BigDecimal ipcaRate) {
        String indexStr = tx.getIndexador() != null ? tx.getIndexador() : a.getIndexador();
        BigDecimal taxaContratada = tx.getTaxa() != null ? tx.getTaxa() : a.getTaxa();

        if (indexStr == null || taxaContratada == null) {
            return valorInicial;
        }
        
        BigDecimal taxaAnual = BigDecimal.ZERO;
        String index = indexStr.toUpperCase().trim();

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

        if (taxaAnual.compareTo(BigDecimal.ZERO) <= 0) {
            return valorInicial;
        }

        double annualRateDouble = taxaAnual.doubleValue() / 100.0;
        double dailyRateDouble = Math.pow(1.0 + annualRateDouble, 1.0 / 365.0) - 1.0;
        
        long days = java.time.temporal.ChronoUnit.DAYS.between(tx.getData(), targetDate);
        if (days <= 0) {
            return valorInicial;
        }
        
        double currentBalance = valorInicial.doubleValue() * Math.pow(1.0 + dailyRateDouble, days);
        if (currentBalance < 0.0) {
            currentBalance = 0.0;
        }
        return BigDecimal.valueOf(currentBalance).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> calcularPatrimonioEInvestidoNaData(
            LocalDate targetDate,
            List<Ativo> ativos,
            List<InvestimentoLancamento> todosLancamentos,
            BigDecimal selicRate,
            BigDecimal ipcaRate) {
        
        BigDecimal investido = BigDecimal.ZERO;
        BigDecimal patrimonio = BigDecimal.ZERO;

        List<InvestimentoLancamento> lancamentosAteData = todosLancamentos.stream()
                .filter(l -> !l.getData().isAfter(targetDate))
                .toList();

        Map<Long, List<InvestimentoLancamento>> lancamentosPorAtivo = lancamentosAteData.stream()
                .collect(Collectors.groupingBy(l -> l.getAtivo().getId()));

        for (Ativo a : ativos) {
            List<InvestimentoLancamento> txs = lancamentosPorAtivo.getOrDefault(a.getId(), List.of());
            if (txs.isEmpty()) {
                continue;
            }

            if (a.getTipoAtivo() == TipoAtivo.RENDA_FIXA || a.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO) {
                List<InvestimentoLancamento> sortedTxs = txs.stream()
                        .sorted(Comparator.comparing(InvestimentoLancamento::getData)
                                .thenComparing(InvestimentoLancamento::getId))
                        .toList();

                List<InvestimentoLancamento> compras = sortedTxs.stream()
                        .filter(t -> t.getTipoOperacao() == br.com.lesnik.mytwocents.model.TipoOperacao.COMPRA)
                        .toList();

                double totalVendas = sortedTxs.stream()
                        .filter(t -> t.getTipoOperacao() == br.com.lesnik.mytwocents.model.TipoOperacao.VENDA)
                        .mapToDouble(t -> t.getValorTotal().doubleValue())
                        .sum();

                double vendasRestantes = totalVendas;
                for (InvestimentoLancamento tx : compras) {
                    double valorOriginal = tx.getValorTotal().doubleValue();
                    double valorExibido = valorOriginal;
                    if (vendasRestantes > 0) {
                        if (valorOriginal <= vendasRestantes) {
                            vendasRestantes -= valorOriginal;
                            continue;
                        } else {
                            valorExibido = valorOriginal - vendasRestantes;
                            vendasRestantes = 0;
                        }
                    }

                    BigDecimal custoOrig = BigDecimal.valueOf(valorExibido);
                    investido = investido.add(custoOrig);

                    BigDecimal valorAtual = calcularValorAtualLancamentoComValorInicialNaData(tx, a, custoOrig, targetDate, selicRate, ipcaRate);
                    patrimonio = patrimonio.add(valorAtual);
                }
            } else {
                BigDecimal qtd = BigDecimal.ZERO;
                BigDecimal pm = BigDecimal.ZERO;

                List<InvestimentoLancamento> sortedTxs = txs.stream()
                        .sorted(Comparator.comparing(InvestimentoLancamento::getData)
                                .thenComparing(InvestimentoLancamento::getId))
                        .toList();

                for (InvestimentoLancamento l : sortedTxs) {
                    if (l.getTipoOperacao() == TipoOperacao.COMPRA) {
                        BigDecimal custoAnterior = qtd.multiply(pm);
                        BigDecimal custoNovo = l.getQuantidade().multiply(l.getPrecoUnitario());
                        BigDecimal novaQtd = qtd.add(l.getQuantidade());
                        if (novaQtd.compareTo(BigDecimal.ZERO) > 0) {
                            pm = custoAnterior.add(custoNovo).divide(novaQtd, 2, RoundingMode.HALF_UP);
                        }
                        qtd = novaQtd;
                    } else if (l.getTipoOperacao() == TipoOperacao.VENDA) {
                        qtd = qtd.subtract(l.getQuantidade());
                        if (qtd.compareTo(BigDecimal.ZERO) < 0) {
                            qtd = BigDecimal.ZERO;
                        }
                    }
                }

                if (qtd.compareTo(BigDecimal.ZERO) > 0) {
                    investido = investido.add(qtd.multiply(pm));
                    patrimonio = patrimonio.add(qtd.multiply(a.getPrecoAtual()));
                }
            }
        }

        return Map.of("investido", investido, "patrimonio", patrimonio);
    }

    private Map<TipoAtivo, BigDecimal> getProporcoesIdeais() {
        var cfg = aiConfigRepository.findFirstByOrderByIdDesc().orElse(null);
        Map<TipoAtivo, BigDecimal> map = new LinkedHashMap<>();
        map.put(TipoAtivo.ACAO, cfg != null && cfg.getMetaAcao() != null ? cfg.getMetaAcao() : new BigDecimal("25"));
        map.put(TipoAtivo.FII, cfg != null && cfg.getMetaFii() != null ? cfg.getMetaFii() : new BigDecimal("15"));
        map.put(TipoAtivo.RENDA_FIXA, cfg != null && cfg.getMetaRendaFixa() != null ? cfg.getMetaRendaFixa() : new BigDecimal("20"));
        map.put(TipoAtivo.ETF, cfg != null && cfg.getMetaEtf() != null ? cfg.getMetaEtf() : new BigDecimal("15"));
        map.put(TipoAtivo.TESOURO_DIRETO, cfg != null && cfg.getMetaTesouro() != null ? cfg.getMetaTesouro() : new BigDecimal("20"));
        map.put(TipoAtivo.CRIPTO, cfg != null && cfg.getMetaCripto() != null ? cfg.getMetaCripto() : new BigDecimal("5"));
        return map;
    }

    private Map<TipoAtivo, BigDecimal> calcularDistribuicaoIdeal(BigDecimal patrimonioTotal) {
        Map<TipoAtivo, BigDecimal> distribuicaoIdeal = new LinkedHashMap<>();

        if (patrimonioTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return distribuicaoIdeal;
        }

        for (Map.Entry<TipoAtivo, BigDecimal> entry : getProporcoesIdeais().entrySet()) {
            TipoAtivo tipo = entry.getKey();
            BigDecimal percentual = entry.getValue();
            BigDecimal valorIdeal = patrimonioTotal.multiply(percentual)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            distribuicaoIdeal.put(tipo, valorIdeal);
        }

        return distribuicaoIdeal;
    }
}
