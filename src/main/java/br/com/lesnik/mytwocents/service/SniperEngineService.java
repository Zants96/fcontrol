package br.com.lesnik.mytwocents.service;

import br.com.lesnik.mytwocents.dto.*;
import br.com.lesnik.mytwocents.model.*;
import br.com.lesnik.mytwocents.repository.AiConfigRepository;
import br.com.lesnik.mytwocents.repository.AtivoRepository;
import br.com.lesnik.mytwocents.repository.InvestimentoLancamentoRepository;
import br.com.lesnik.mytwocents.model.InvestimentoLancamento;
import br.com.lesnik.mytwocents.model.TipoOperacao;
import br.com.lesnik.mytwocents.model.TipoAtivo;
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
public class SniperEngineService {

    private final AtivoRepository ativoRepository;
    private final AiConfigRepository aiConfigRepository;
    private final InvestimentoLancamentoRepository lancamentoRepository;

    private static final BigDecimal EM_LOCK_MIN_PCT = new BigDecimal("25.0");

    // Proporções ideais da carteira por tipo de ativo
    private static final Map<TipoAtivo, BigDecimal> PROPORCOES_POR_TIPO = new LinkedHashMap<>();
    static {
        PROPORCOES_POR_TIPO.put(TipoAtivo.ACAO, new BigDecimal("25"));
        PROPORCOES_POR_TIPO.put(TipoAtivo.FII, new BigDecimal("15"));
        PROPORCOES_POR_TIPO.put(TipoAtivo.RENDA_FIXA, new BigDecimal("20"));
        PROPORCOES_POR_TIPO.put(TipoAtivo.ETF, new BigDecimal("15"));
        PROPORCOES_POR_TIPO.put(TipoAtivo.TESOURO_DIRETO, new BigDecimal("20"));
        PROPORCOES_POR_TIPO.put(TipoAtivo.CRIPTO, new BigDecimal("5"));
    }

    @Transactional(readOnly = true)
    public SniperOverviewDTO obterOverview() {
        List<Ativo> ativos = ativoRepository.findByAtivoTrueOrderByTipoAtivoAscTickerAsc();
        AiConfig config = getAiConfig();
        BigDecimal targetBox = config.getEmergencyBoxTarget() != null 
                ? config.getEmergencyBoxTarget() 
                : new BigDecimal("20000.00");

        BigDecimal totalSeguranca = calcularTotalSeguranca(ativos);
        BigDecimal patrimonioTotal = calcularPatrimonioTotal(ativos);

        BigDecimal pctSeguranca = BigDecimal.ZERO;
        if (patrimonioTotal.compareTo(BigDecimal.ZERO) > 0) {
            pctSeguranca = totalSeguranca.divide(patrimonioTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        boolean isLocked = (totalSeguranca.compareTo(targetBox) < 0);
        // 🔒 O lock NÃO depende mais do percentual mínimo isolado;
        // ele só trava quando o valor em R$ da reserva está abaixo da meta,
        // OU quando o percentual está abaixo de 25% e o valor está abaixo da meta.

        BigDecimal valorFaltante = isLocked && targetBox.compareTo(totalSeguranca) > 0
                ? targetBox.subtract(totalSeguranca)
                : BigDecimal.ZERO;

        List<TacticalOpportunityDTO> oportunidades = calcularOportunidadesTaticas(ativos);

        return SniperOverviewDTO.builder()
                .emergencyLock(isLocked)
                .totalSeguranca(totalSeguranca)
                .emergencyBoxTarget(targetBox)
                .pctSeguranca(pctSeguranca.setScale(2, RoundingMode.HALF_UP))
                .patrimonioTotal(patrimonioTotal)
                .valorFaltanteSeguranca(valorFaltante.setScale(2, RoundingMode.HALF_UP))
                .quantidadeOportunidadesTaticas(oportunidades.size())
                .oportunidades(oportunidades)
                .build();
    }

    @Transactional(readOnly = true)
    public AporteCalculoResponseDTO calcularAporte(BigDecimal valorAporte) {
        if (valorAporte == null || valorAporte.compareTo(BigDecimal.ZERO) <= 0) {
            return AporteCalculoResponseDTO.builder()
                    .valorAporteSolicitado(BigDecimal.ZERO)
                    .valorAporteEfetivo(BigDecimal.ZERO)
                    .sobraCaixa(BigDecimal.ZERO)
                    .emergencyLockAtivo(false)
                    .mensagemLock("Informe um valor de aporte válido.")
                    .itens(Collections.emptyList())
                    .build();
        }

        SniperOverviewDTO overview = obterOverview();
        List<Ativo> ativos = ativoRepository.findByAtivoTrueOrderByTipoAtivoAscTickerAsc();

        BigDecimal disponivel = valorAporte;
        List<AporteItemDTO> itens = new ArrayList<>();
        boolean lockAtivo = overview.isEmergencyLock();
        String msgLock = null;

        // Passo 1: Cadeado de Segurança (Emergency Lock)
        if (lockAtivo && overview.getValorFaltanteSeguranca().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal alocarSeguranca = disponivel.min(overview.getValorFaltanteSeguranca());

            // Procura ativo de segurança
            Ativo ativoSeguranca = ativos.stream()
                    .filter(a -> a.getCategoriaTatica() == CategoriaTatica.SEGURANCA
                              || a.getTipoAtivo() == TipoAtivo.RENDA_FIXA
                              || a.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO)
                    .findFirst()
                    .orElse(ativos.isEmpty() ? null : ativos.get(0));

            if (ativoSeguranca != null) {
                BigDecimal preco = ativoSeguranca.getPrecoAtual().compareTo(BigDecimal.ZERO) > 0
                        ? ativoSeguranca.getPrecoAtual()
                        : BigDecimal.ONE;

                BigDecimal cotas = alocarSeguranca.divide(preco, 4, RoundingMode.HALF_UP);
                BigDecimal pctAporte = alocarSeguranca.divide(valorAporte, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

                itens.add(AporteItemDTO.builder()
                        .ativoId(ativoSeguranca.getId())
                        .ticker(ativoSeguranca.getTicker())
                        .nome(ativoSeguranca.getNome() != null ? ativoSeguranca.getNome() : ativoSeguranca.getTicker())
                        .categoriaTatica(getCategoriaTaticaEfetiva(ativoSeguranca))
                        .tipoAtivo(ativoSeguranca.getTipoAtivo())
                        .cotasEstimadas(cotas)
                        .precoAtual(preco)
                        .valorAlocado(alocarSeguranca.setScale(2, RoundingMode.HALF_UP))
                        .percentualAporte(pctAporte.setScale(2, RoundingMode.HALF_UP))
                        .deficit(overview.getValorFaltanteSeguranca())
                        .ativoSeguranca(true)
                        .build());

                disponivel = disponivel.subtract(alocarSeguranca);
                msgLock = String.format("Cadeado de Segurança ATIVO: R$ %.2f direcionado para recompor a Reserva de Emergência.", alocarSeguranca);
            }
        }

        // Passo 2: Rebalanceamento Proporcional Multiativos (Smart Split)
        if (disponivel.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal patrimonioFuturo = overview.getPatrimonioTotal().add(valorAporte);

            List<Ativo> ativosElegiveis = ativos.stream()
                    .filter(a -> a.getTipoAtivo() != null && getProporcoesIdeais().containsKey(a.getTipoAtivo()))
                    .filter(a -> !isTesouroAproximandoVencimento(a))
                    .collect(Collectors.toList());

            if (!ativosElegiveis.isEmpty()) {
                // Agrupa por tipo e calcula o valor ideal por tipo
                Map<TipoAtivo, List<Ativo>> ativosPorTipo = ativosElegiveis.stream()
                        .collect(Collectors.groupingBy(Ativo::getTipoAtivo));

                Map<Ativo, BigDecimal> deficits = new LinkedHashMap<>();
                BigDecimal somaDeficits = BigDecimal.ZERO;

                for (Map.Entry<TipoAtivo, List<Ativo>> entry : ativosPorTipo.entrySet()) {
                    TipoAtivo tipo = entry.getKey();
                    List<Ativo> ativosDoTipo = entry.getValue();

                    BigDecimal pctIdeal = getProporcoesIdeais().get(tipo);
                    if (pctIdeal == null || pctIdeal.compareTo(BigDecimal.ZERO) <= 0) continue;

                    BigDecimal valorIdealTotal = patrimonioFuturo.multiply(pctIdeal)
                            .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

                    // Soma o valor atual de todos os ativos deste tipo
                    BigDecimal valorAtualTotal = ativosDoTipo.stream()
                            .map(a -> a.getQuantidade().multiply(a.getPrecoAtual()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal deficitTotal = valorIdealTotal.subtract(valorAtualTotal);

                    if (deficitTotal.compareTo(BigDecimal.ZERO) > 0) {
                        // Rateia o déficit proporcionalmente ao valor de cada ativo
                        for (Ativo a : ativosDoTipo) {
                            BigDecimal valorAtivo = a.getQuantidade().multiply(a.getPrecoAtual());
                            BigDecimal proporcao = valorAtualTotal.compareTo(BigDecimal.ZERO) > 0
                                    ? valorAtivo.divide(valorAtualTotal, 6, RoundingMode.HALF_UP)
                                    : BigDecimal.ONE.divide(BigDecimal.valueOf(ativosDoTipo.size()), 6, RoundingMode.HALF_UP);
                            BigDecimal deficitAtivo = deficitTotal.multiply(proporcao).setScale(2, RoundingMode.HALF_UP);
                            if (deficitAtivo.compareTo(BigDecimal.ZERO) > 0) {
                                deficits.put(a, deficitAtivo);
                                somaDeficits = somaDeficits.add(deficitAtivo);
                            }
                        }
                    }
                }

                // ─── Tesouro Direto: verificar déficit considerando TODOS os títulos (inclusive próximos ao vencimento) ───
                // Se o Tesouro Direto total está abaixo do ideal (20%), gerar um item "Novo Tesouro Direto"
                List<Ativo> todosTesouros = ativos.stream()
                        .filter(a -> a.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO)
                        .collect(Collectors.toList());

                if (!todosTesouros.isEmpty()) {
                    BigDecimal pctTesouroIdeal = getProporcoesIdeais().get(TipoAtivo.TESOURO_DIRETO);
                    if (pctTesouroIdeal != null && pctTesouroIdeal.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal valorIdealTesouro = patrimonioFuturo.multiply(pctTesouroIdeal)
                                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

                        BigDecimal valorAtualTesouro = todosTesouros.stream()
                                .map(a -> a.getQuantidade().multiply(a.getPrecoAtual()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        BigDecimal deficitTesouro = valorIdealTesouro.subtract(valorAtualTesouro);

                        if (deficitTesouro.compareTo(BigDecimal.ZERO) > 0) {
                            // Verifica se já existe um item de Tesouro no rateio (algum título elegível)
                            boolean jaTemTesouroNoRateio = ativosElegiveis.stream()
                                    .anyMatch(a -> a.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO);

                            if (!jaTemTesouroNoRateio) {
                                // Adiciona déficit virtual para "Novo Tesouro Direto"
                                deficits.put(null, deficitTesouro);
                                somaDeficits = somaDeficits.add(deficitTesouro);
                            }
                        }
                    }
                }

                BigDecimal disponivelParaRateio = disponivel;

                if (somaDeficits.compareTo(BigDecimal.ZERO) > 0) {
                    for (Map.Entry<Ativo, BigDecimal> entry : deficits.entrySet()) {
                        Ativo a = entry.getKey();
                        BigDecimal deficit = entry.getValue();

                        BigDecimal proporcao = deficit.divide(somaDeficits, 6, RoundingMode.HALF_UP);
                        BigDecimal share = disponivelParaRateio.multiply(proporcao).setScale(2, RoundingMode.HALF_UP);
                        share = share.min(deficit);

                        if (share.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal pctAporte = share.divide(valorAporte, 4, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100"));

                            if (a != null) {
                                BigDecimal preco = a.getPrecoAtual().compareTo(BigDecimal.ZERO) > 0
                                        ? a.getPrecoAtual()
                                        : BigDecimal.ONE;

                                BigDecimal cotas = share.divide(preco, 4, RoundingMode.HALF_UP);

                                // Atualiza se já existir no item de segurança ou cria novo
                                Optional<AporteItemDTO> existente = itens.stream()
                                        .filter(i -> i.getAtivoId() != null && i.getAtivoId().equals(a.getId()))
                                        .findFirst();

                                if (existente.isPresent()) {
                                    AporteItemDTO item = existente.get();
                                    item.setValorAlocado(item.getValorAlocado().add(share));
                                    item.setCotasEstimadas(item.getValorAlocado().divide(preco, 4, RoundingMode.HALF_UP));
                                    item.setPercentualAporte(item.getValorAlocado().divide(valorAporte, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
                                } else {
                                    itens.add(AporteItemDTO.builder()
                                            .ativoId(a.getId())
                                            .ticker(a.getTicker())
                                            .nome(a.getNome() != null ? a.getNome() : a.getTicker())
                                            .categoriaTatica(getCategoriaTaticaEfetiva(a))
                                            .tipoAtivo(a.getTipoAtivo())
                                            .cotasEstimadas(cotas)
                                            .precoAtual(preco)
                                            .valorAlocado(share)
                                            .percentualAporte(pctAporte.setScale(2, RoundingMode.HALF_UP))
                                            .deficit(deficit.setScale(2, RoundingMode.HALF_UP))
                                            .ativoSeguranca(false)
                                            .build());
                                }
                            } else {
                                // Ativo nulo = Novo Tesouro Direto (sugestão de compra de novo título)
                                itens.add(AporteItemDTO.builder()
                                        .ticker("Novo Tesouro Direto")
                                        .nome("Novo Tesouro Direto")
                                        .categoriaTatica(CategoriaTatica.RENDA)
                                        .tipoAtivo(TipoAtivo.TESOURO_DIRETO)
                                        .cotasEstimadas(BigDecimal.ZERO)
                                        .precoAtual(BigDecimal.ZERO)
                                        .valorAlocado(share)
                                        .percentualAporte(pctAporte.setScale(2, RoundingMode.HALF_UP))
                                        .deficit(deficit.setScale(2, RoundingMode.HALF_UP))
                                        .ativoSeguranca(false)
                                        .build());
                            }

                            disponivel = disponivel.subtract(share);
                        }
                    }
                } else {
                    // Sem déficit específico: rateia de acordo com a metaPercent
                    BigDecimal somaMetas = ativosElegiveis.stream()
                            .map(Ativo::getMetaPercent)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (somaMetas.compareTo(BigDecimal.ZERO) > 0) {
                        for (Ativo a : ativosElegiveis) {
                            BigDecimal prop = a.getMetaPercent().divide(somaMetas, 6, RoundingMode.HALF_UP);
                            BigDecimal share = disponivelParaRateio.multiply(prop).setScale(2, RoundingMode.HALF_UP);

                            if (share.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal preco = a.getPrecoAtual().compareTo(BigDecimal.ZERO) > 0
                                        ? a.getPrecoAtual()
                                        : BigDecimal.ONE;

                                BigDecimal cotas = share.divide(preco, 4, RoundingMode.HALF_UP);
                                BigDecimal pctAporte = share.divide(valorAporte, 4, RoundingMode.HALF_UP)
                                        .multiply(new BigDecimal("100"));

                                itens.add(AporteItemDTO.builder()
                                        .ativoId(a.getId())
                                        .ticker(a.getTicker())
                                        .nome(a.getNome() != null ? a.getNome() : a.getTicker())
                                        .categoriaTatica(getCategoriaTaticaEfetiva(a))
                                        .tipoAtivo(a.getTipoAtivo())
                                        .cotasEstimadas(cotas)
                                        .precoAtual(preco)
                                        .valorAlocado(share)
                                        .percentualAporte(pctAporte.setScale(2, RoundingMode.HALF_UP))
                                        .deficit(BigDecimal.ZERO)
                                        .ativoSeguranca(false)
                                        .build());

                                disponivel = disponivel.subtract(share);
                            }
                        }
                    }
                }
            }
        }

        BigDecimal valorEfetivo = valorAporte.subtract(disponivel);

        return AporteCalculoResponseDTO.builder()
                .valorAporteSolicitado(valorAporte)
                .valorAporteEfetivo(valorEfetivo.setScale(2, RoundingMode.HALF_UP))
                .sobraCaixa(disponivel.setScale(2, RoundingMode.HALF_UP))
                .emergencyLockAtivo(lockAtivo)
                .mensagemLock(msgLock)
                .itens(itens)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TacticalOpportunityDTO> listarOportunidadesTaticas() {
        List<Ativo> ativos = ativoRepository.findByAtivoTrueOrderByTipoAtivoAscTickerAsc();
        return calcularOportunidadesTaticas(ativos);
    }

    @Transactional
    public Ativo atualizarTaticaAtivo(Long ativoId, CategoriaTatica categoriaTatica, Boolean ciclico, Boolean estrutural) {
        Ativo ativo = ativoRepository.findById(ativoId)
                .orElseThrow(() -> new NoSuchElementException("Ativo não encontrado: " + ativoId));

        if (categoriaTatica != null) ativo.setCategoriaTatica(categoriaTatica);
        if (ciclico != null) ativo.setCiclico(ciclico);
        if (estrutural != null) ativo.setEstrutural(estrutural);

        return ativoRepository.save(ativo);
    }

    @Transactional
    public AiConfig atualizarConfigEmergencia(BigDecimal targetBox, BigDecimal monthlyIncome) {
        AiConfig config = getAiConfig();
        if (targetBox != null && targetBox.compareTo(BigDecimal.ZERO) >= 0) {
            config.setEmergencyBoxTarget(targetBox);
        }
        if (monthlyIncome != null && monthlyIncome.compareTo(BigDecimal.ZERO) >= 0) {
            config.setMonthlyIncome(monthlyIncome);
        }
        return aiConfigRepository.save(config);
    }

    // ─── MÉTODOS PRIVADOS AUXILIARES ─────────────────────────────────────────

    private AiConfig getAiConfig() {
        return aiConfigRepository.findFirstByOrderByIdDesc()
                .orElseGet(() -> AiConfig.builder()
                        .apiKey("")
                        .provider("gemini")
                        .modelo("gemini-2.5-flash")
                        .emergencyBoxTarget(new BigDecimal("20000.00"))
                        .monthlyIncome(new BigDecimal("5000.00"))
                        .build());
    }

    private BigDecimal calcularTotalSeguranca(List<Ativo> ativos) {
        BigDecimal total = BigDecimal.ZERO;

        for (Ativo a : ativos) {
            String ticker = a.getTicker() != null ? a.getTicker().toUpperCase() : "";

            boolean isElegivel = false;

            // Incluir CDB na RENDA_FIXA
            if (a.getTipoAtivo() == TipoAtivo.RENDA_FIXA && ticker.contains("CDB")) {
                isElegivel = true;
            }

            // Incluir Tesouro SELIC no TESOURO_DIRETO
            if (a.getTipoAtivo() == TipoAtivo.TESOURO_DIRETO && ticker.contains("SELIC")) {
                isElegivel = true;
            }

            // Incluir Poupança na RENDA_FIXA
            if (a.getTipoAtivo() == TipoAtivo.RENDA_FIXA && ticker.contains("POUPANCA")) {
                isElegivel = true;
            }

            // Incluir ativos marcados manualmente como SEGURANCA
            if (a.getCategoriaTatica() == CategoriaTatica.SEGURANCA) {
                isElegivel = true;
            }

            if (!isElegivel) continue;

            // Calcular valor real baseado nos lançamentos (para CDB, Poupança, etc.)
            BigDecimal valorReal = calcularValorRealAtivo(a);
            total = total.add(valorReal);
        }

        log.info("Total Seguranca calculado: R$ {}", total);
        return total;
    }

    private BigDecimal calcularValorRealAtivo(Ativo a) {
        List<InvestimentoLancamento> txs = lancamentoRepository.findByAtivoIdOrderByDataDesc(a.getId());

        if (txs == null || txs.isEmpty()) {
            // Fallback para o valor simples se não houver lançamentos
            return a.getQuantidade().multiply(a.getPrecoAtual());
        }

        // Ordenar por data crescente
        txs.sort(Comparator.comparing(InvestimentoLancamento::getData)
                .thenComparing(InvestimentoLancamento::getId));

        double currentBalance = 0.0;

        for (InvestimentoLancamento tx : txs) {
            if (tx.getTipoOperacao() == TipoOperacao.COMPRA) {
                currentBalance += tx.getValorTotal().doubleValue();
            } else if (tx.getTipoOperacao() == TipoOperacao.VENDA) {
                currentBalance -= tx.getValorTotal().doubleValue();
            }
        }

        if (currentBalance < 0.0) {
            currentBalance = 0.0;
        }

        return BigDecimal.valueOf(currentBalance).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularPatrimonioTotal(List<Ativo> ativos) {
        return ativos.stream()
                .map(a -> a.getQuantidade().multiply(a.getPrecoAtual()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<TacticalOpportunityDTO> calcularOportunidadesTaticas(List<Ativo> ativos) {
        List<TacticalOpportunityDTO> ops = new ArrayList<>();

        for (Ativo a : ativos) {
            boolean ehCiclicoOuVariavel = a.isCiclico()
                    || a.getTipoAtivo() == TipoAtivo.ACAO
                    || a.getTipoAtivo() == TipoAtivo.FII
                    || a.getTipoAtivo() == TipoAtivo.CRIPTO
                    || a.getTipoAtivo() == TipoAtivo.ETF;

            if (!ehCiclicoOuVariavel) continue;
            if (a.getPrecoMedio() == null || a.getPrecoMedio().compareTo(BigDecimal.ZERO) <= 0) continue;
            if (a.getPrecoAtual() == null || a.getPrecoAtual().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal diff = a.getPrecoAtual().subtract(a.getPrecoMedio());
            BigDecimal varPct = diff.divide(a.getPrecoMedio(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            double var = varPct.doubleValue();

            // Gatilhos de Compra (Quedas)
            if (var <= -20.0) {
                int nivel;
                String acao;
                BigDecimal prop;

                if (var <= -50.0) {
                    nivel = 3;
                    acao = String.format("Queda severa (%.1f%% ≤ -50%%): Compra Escalonada Nível 3 (+50%% da posição)", var);
                    prop = new BigDecimal("0.50");
                } else if (var <= -30.0) {
                    nivel = 2;
                    acao = String.format("Queda forte (%.1f%% entre -30%% e -49%%): Compra Escalonada Nível 2 (+30%% da posição)", var);
                    prop = new BigDecimal("0.30");
                } else {
                    nivel = 1;
                    acao = String.format("Queda tática (%.1f%% entre -20%% e -29%%): Compra Escalonada Nível 1 (+10%% da posição)", var);
                    prop = new BigDecimal("0.10");
                }

                BigDecimal sugestaoQtde = a.getQuantidade().multiply(prop).setScale(4, RoundingMode.HALF_UP);
                if (sugestaoQtde.compareTo(BigDecimal.ZERO) == 0) {
                    sugestaoQtde = BigDecimal.ONE;
                }

                ops.add(TacticalOpportunityDTO.builder()
                        .ativoId(a.getId())
                        .ticker(a.getTicker())
                        .nome(a.getNome() != null ? a.getNome() : a.getTicker())
                        .tipoAtivo(a.getTipoAtivo())
                        .categoriaTatica(getCategoriaTaticaEfetiva(a))
                        .ciclico(a.isCiclico())
                        .estrutural(a.isEstrutural())
                        .precoAtual(a.getPrecoAtual())
                        .precoMedio(a.getPrecoMedio())
                        .variacaoPercent(varPct.setScale(2, RoundingMode.HALF_UP))
                        .tipoGatilho("COMPRA")
                        .nivelGatilho(nivel)
                        .sugestaoAcao(acao)
                        .sugestaoQuantidade(sugestaoQtde)
                        .build());
                                    }
                                    // Gatilhos de Venda (Altas, apenas para não estruturais)
                                    else if (!a.isEstrutural() && var >= 30.0) {
                                        int nivel;
                                        String acao;
                                        BigDecimal prop;

                                        if (var >= 100.0) {
                                            nivel = 5;
                                            acao = String.format("Valorização extrema (%.1f%% ≥ +100%%): Zerar Posição / Realização Total de Lucro", var);
                                            prop = BigDecimal.ONE;
                                        } else if (var >= 60.0) {
                                            nivel = 4;
                                            acao = String.format("Valorização alta (%.1f%% entre +60%% e +99%%): Venda Parcial de 40%% da posição", var);
                                            prop = new BigDecimal("0.40");
                                        } else if (var >= 50.0) {
                                            nivel = 3;
                                            acao = String.format("Valorização expressiva (%.1f%% entre +50%% e +59%%): Venda Parcial de 30%% da posição", var);
                                            prop = new BigDecimal("0.30");
                                        } else if (var >= 40.0) {
                                            nivel = 2;
                                            acao = String.format("Valorização moderada (%.1f%% entre +40%% e +49%%): Venda Parcial de 20%% da posição", var);
                                            prop = new BigDecimal("0.20");
                                        } else {
                                            nivel = 1;
                                            acao = String.format("Valorização inicial (%.1f%% entre +30%% e +39%%): Venda Parcial de 10%% da posição", var);
                                            prop = new BigDecimal("0.10");
                                        }

                BigDecimal sugestaoQtde = a.getQuantidade().multiply(prop).setScale(4, RoundingMode.HALF_UP);

                                        ops.add(TacticalOpportunityDTO.builder()
                                                .ativoId(a.getId())
                                                .ticker(a.getTicker())
                                                .nome(a.getNome() != null ? a.getNome() : a.getTicker())
                                                .tipoAtivo(a.getTipoAtivo())
                                                .categoriaTatica(getCategoriaTaticaEfetiva(a))
                                                .ciclico(a.isCiclico())
                                                .estrutural(a.isEstrutural())
                                                .precoAtual(a.getPrecoAtual())
                                                .precoMedio(a.getPrecoMedio())
                                                .variacaoPercent(varPct.setScale(2, RoundingMode.HALF_UP))
                                                .tipoGatilho("VENDA")
                        .nivelGatilho(nivel)
                        .sugestaoAcao(acao)
                        .sugestaoQuantidade(sugestaoQtde)
                        .build());
            }
        }

        return ops;
    }

    /**
     * Verifica se um Tesouro Direto está com vencimento próximo ao ponto
     * de não poder mais receber aportes.
     * SELIC: excluir se faltarem menos de 2 anos.
     * IPCA: excluir se faltarem menos de 3 anos.
     */
    private boolean isTesouroAproximandoVencimento(Ativo a) {
        if (a.getTipoAtivo() != TipoAtivo.TESOURO_DIRETO) return false;
        if (a.getDataVencimento() == null) return false;

        String indexador = a.getIndexador() != null ? a.getIndexador().toUpperCase() : "";
        java.time.LocalDate hoje = java.time.LocalDate.now();
        long anosRestantes = java.time.temporal.ChronoUnit.YEARS.between(hoje, a.getDataVencimento());

        if (indexador.contains("SELIC")) {
            return anosRestantes < 2;
        } else if (indexador.contains("IPCA")) {
            return anosRestantes < 3;
        }
        // Outros indexadores: usa regra padrão de 2 anos
        return anosRestantes < 2;
    }

    /**
     * Retorna a categoria tática efetiva de um ativo.
     * Usa a inferência automática quando o valor armazenado é o padrão RENDA
     * mas o ativo claramente pertence a outra categoria (ex: ação → CRESCIMENTO).
     * Se o usuário definiu manualmente uma categoria diferente de RENDA, mantém a manual.
     */
    private CategoriaTatica getCategoriaTaticaEfetiva(Ativo a) {
        CategoriaTatica stored = a.getCategoriaTatica();
        if (stored != null && stored != CategoriaTatica.RENDA) {
            return stored; // Usuário definiu manualmente
        }
        // Fallback para inferência automática
        CategoriaTatica inferida = br.com.lesnik.mytwocents.util.CategoriaTaticaUtils.inferirCategoriaTatica(a);
        if (inferida != CategoriaTatica.RENDA) {
            return inferida;
        }
        // Se a inferência também retorna RENDA, mantém o valor armazenado (se houver)
        return stored != null ? stored : CategoriaTatica.RENDA;
    }

    public AiConfig getConfig() {
        return getAiConfig();
    }

    public AiConfig atualizarConfig(AiConfig config) {
        return aiConfigRepository.save(config);
    }

    /**
     * Retorna as proporções ideais dinâmicas do banco (AiConfig).
     * Se não configuradas, usa os valores padrão.
     */
    private Map<TipoAtivo, BigDecimal> getProporcoesIdeais() {
        AiConfig cfg = getAiConfig();
        Map<TipoAtivo, BigDecimal> map = new LinkedHashMap<>();
        map.put(TipoAtivo.ACAO, cfg.getMetaAcao() != null ? cfg.getMetaAcao() : new BigDecimal("25"));
        map.put(TipoAtivo.FII, cfg.getMetaFii() != null ? cfg.getMetaFii() : new BigDecimal("15"));
        map.put(TipoAtivo.RENDA_FIXA, cfg.getMetaRendaFixa() != null ? cfg.getMetaRendaFixa() : new BigDecimal("20"));
        map.put(TipoAtivo.ETF, cfg.getMetaEtf() != null ? cfg.getMetaEtf() : new BigDecimal("15"));
        map.put(TipoAtivo.TESOURO_DIRETO, cfg.getMetaTesouro() != null ? cfg.getMetaTesouro() : new BigDecimal("20"));
        map.put(TipoAtivo.CRIPTO, cfg.getMetaCripto() != null ? cfg.getMetaCripto() : new BigDecimal("5"));
        return map;
    }
}
