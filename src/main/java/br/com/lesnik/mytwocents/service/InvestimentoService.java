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
import java.time.LocalDate;
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

        if (dto.getTipoAtivo() != null && ativo.getTipoAtivo() != dto.getTipoAtivo()) {
            ativo.setTipoAtivo(dto.getTipoAtivo());
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

        // Gera lançamento financeiro cruzado
        Lancamento fin = Lancamento.builder()
                .descricao((dto.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Dividendo: " : 
                            dto.getTipoOperacao() == TipoOperacao.COMPRA ? "Compra de Ativo: " : "Venda de Ativo: ") 
                           + ativo.getTicker())
                .categoria(dto.getTipoOperacao() == TipoOperacao.COMPRA ? Categoria.GASTO : Categoria.RECEITA)
                .subcategoria(dto.getTipoOperacao() == TipoOperacao.COMPRA ? "Investimentos" :
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
                fin.setCategoria(lancamento.getTipoOperacao() == TipoOperacao.COMPRA ? Categoria.GASTO : Categoria.RECEITA);
                fin.setSubcategoria(lancamento.getTipoOperacao() == TipoOperacao.COMPRA ? "Investimentos" :
                                    lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO ? "Proventos" : "Resgate de Investimentos");
                fin.setValor(lancamento.getTipoOperacao() == TipoOperacao.DIVIDENDO ? lancamento.getValorLiquido() : lancamento.getValorTotal());
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
                    .subcategoria(lancamento.getTipoOperacao() == TipoOperacao.COMPRA ? "Investimentos" :
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
        if (dto.getTipoAtivo() != null && lancamento.getAtivo().getTipoAtivo() != dto.getTipoAtivo()) {
            Ativo ativo = lancamento.getAtivo();
            ativo.setTipoAtivo(dto.getTipoAtivo());
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
            
            // Dividendos no mês
            BigDecimal dividendosNoMes = todosLancamentos.stream()
                    .filter(l -> l.getTipoOperacao() == TipoOperacao.DIVIDENDO && java.time.YearMonth.from(l.getData()).equals(ym))
                    .map(InvestimentoLancamento::getValorTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, BigDecimal> valores = calcularPatrimonioEInvestidoNaData(endOfMonth, ativos, todosLancamentos, selicRate, ipcaRate);

            evolucaoInvestido12m.add(valores.get("investido"));
            evolucaoPatrimonio12m.add(valores.get("patrimonio"));
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
                .valorLiquido(l.getValorLiquido() != null ? l.getValorLiquido() : l.getValorTotal())
                .dataVencimento(l.getDataVencimento())
                .indexador(l.getIndexador())
                .taxa(l.getTaxa())
                .tipoProvento(l.getTipoProvento())
                .build();
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

    private BigDecimal calcularValorAtualLancamentoComValorInicial(
            InvestimentoLancamento tx, Ativo a, BigDecimal valorInicial, BigDecimal selicRate, BigDecimal ipcaRate) {
        return calcularValorAtualLancamentoComValorInicialNaData(tx, a, valorInicial, java.time.LocalDate.now(), selicRate, ipcaRate);
    }

    private Map<String, BigDecimal> calcularPatrimonioEInvestidoNaData(
            LocalDate targetDate,
            List<Ativo> ativos,
            List<InvestimentoLancamento> todosLancamentos,
            BigDecimal selicRate,
            BigDecimal ipcaRate) {
        
        BigDecimal investido = BigDecimal.ZERO;
        BigDecimal patrimonio = BigDecimal.ZERO;

        // Filtra lançamentos até targetDate
        List<InvestimentoLancamento> lancamentosAteData = todosLancamentos.stream()
                .filter(l -> !l.getData().isAfter(targetDate))
                .toList();

        // Agrupa lançamentos por Ativo
        Map<Long, List<InvestimentoLancamento>> lancamentosPorAtivo = lancamentosAteData.stream()
                .collect(Collectors.groupingBy(l -> l.getAtivo().getId()));

        for (Ativo a : ativos) {
            List<InvestimentoLancamento> txs = lancamentosPorAtivo.getOrDefault(a.getId(), List.of());
            if (txs.isEmpty()) {
                continue;
            }

            if (a.getTipoAtivo() == TipoAtivo.RENDA_FIXA || a.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO) {
                // Renda Fixa / Tesouro Direto: FIFO por compras e vendas com juros compostos individuais
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
                            continue; // Totalmente resgatado
                        } else {
                            valorExibido = valorOriginal - vendasRestantes;
                            vendasRestantes = 0;
                        }
                    }

                    BigDecimal custoOrig = BigDecimal.valueOf(valorExibido);
                    investido = investido.add(custoOrig);

                    // Calcula o valor atualizado na targetDate
                    BigDecimal valorAtual = calcularValorAtualLancamentoComValorInicialNaData(tx, a, custoOrig, targetDate, selicRate, ipcaRate);
                    patrimonio = patrimonio.add(valorAtual);
                }
            } else {
                // Ações / FIIs / ETFs / Cripto: Custo médio normal
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
}
