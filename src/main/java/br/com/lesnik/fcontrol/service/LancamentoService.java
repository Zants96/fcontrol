package br.com.lesnik.fcontrol.service;

import br.com.lesnik.fcontrol.dto.DashboardDTO;
import br.com.lesnik.fcontrol.dto.LancamentoDTO;
import br.com.lesnik.fcontrol.model.Categoria;
import br.com.lesnik.fcontrol.model.Lancamento;
import br.com.lesnik.fcontrol.repository.LancamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LancamentoService {

    private final LancamentoRepository repository;

    /** Categorias que representam saídas de dinheiro (gastos) para cálculos do dashboard */
    private static final List<Categoria> CATS_GASTO = List.of(Categoria.GASTO, Categoria.GASTO_FIXO, Categoria.ASSINATURA);

    // ─── CRUD ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LancamentoDTO> listarPorAno(Integer ano) {
        return repository.findByAnoOrderByMesAscCategoriaAscSubcategoriaAsc(ano)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LancamentoDTO> listarPorAnoEMes(Integer ano, Integer mes) {
        return repository.findByAnoAndMesOrderByCategoriaAscSubcategoriaAsc(ano, mes)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LancamentoDTO> listarPorAnoECategoria(Integer ano, Categoria categoria) {
        return repository.findByAnoAndCategoriaOrderByMesAscSubcategoriaAsc(ano, categoria)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LancamentoDTO> listarPorAnoMesECategoria(Integer ano, Integer mes, Categoria categoria) {
        return repository.findByAnoAndMesAndCategoriaOrderBySubcategoriaAsc(ano, mes, categoria)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public LancamentoDTO criar(LancamentoDTO dto) {
        int parcelas = dto.getParcelas() != null && dto.getParcelas() > 1 ? dto.getParcelas() : 1;
        String grupoId = parcelas > 1 ? UUID.randomUUID().toString() : null;
        Lancamento first = null;

        int currentMes = dto.getMes();
        int currentAno = dto.getAno();

        String baseDesc = limparDescricao(dto.getDescricao());
        for (int i = 0; i < parcelas; i++) {
            Lancamento l = toEntity(dto);
            l.setMes(currentMes);
            l.setAno(currentAno);
            
            if (parcelas > 1) {
                l.setDescricao(baseDesc + " (" + (i + 1) + "/" + parcelas + ")");
                l.setGrupoId(grupoId);
                l.setParcelaActual(i + 1);
                l.setTotalParcelas(parcelas);
            }

            Lancamento saved = repository.save(l);
            if (i == 0) first = saved;

            currentMes++;
            if (currentMes > 12) {
                currentMes = 1;
                currentAno++;
            }
        }

        return toDTO(first != null ? first : repository.save(toEntity(dto)));
    }

    @Transactional
    public LancamentoDTO atualizar(Long id, LancamentoDTO dto) {
        Lancamento lancamento = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lançamento não encontrado: " + id));

        int originalTotal = lancamento.getTotalParcelas() != null ? lancamento.getTotalParcelas() : 1;
        int novoTotal = dto.getParcelas() != null ? dto.getParcelas() : originalTotal;
        String grupoId = lancamento.getGrupoId();
        String originalBaseDesc = limparDescricao(lancamento.getDescricao());

        // 1. Atualiza o registro atual e limpa descrição se for grupo
        String baseDesc = limparDescricao(dto.getDescricao());
        lancamento.setDescricao(grupoId != null ? baseDesc + " (" + (lancamento.getParcelaActual() != null ? lancamento.getParcelaActual() : 1) + "/" + novoTotal + ")" : dto.getDescricao());
        lancamento.setCategoria(dto.getCategoria());
        lancamento.setSubcategoria(dto.getSubcategoria());
        lancamento.setValor(dto.getValor());
        lancamento.setMes(dto.getMes());
        lancamento.setAno(dto.getAno());

        // Se o total mudou, a descrição base mudou, a data mudou ou categorias mudaram, e é um grupo
        boolean dataMudou = dto.getMes() != lancamento.getMes() || dto.getAno() != lancamento.getAno();
        boolean categoriasMudaram = !dto.getCategoria().equals(lancamento.getCategoria()) || !java.util.Objects.equals(dto.getSubcategoria(), lancamento.getSubcategoria());

        if (novoTotal != originalTotal || (grupoId != null && (!baseDesc.equals(originalBaseDesc) || dataMudou || categoriasMudaram))) {
            if (grupoId == null && novoTotal > 1) {
                grupoId = UUID.randomUUID().toString();
                lancamento.setGrupoId(grupoId);
                lancamento.setParcelaActual(1);
                lancamento.setTotalParcelas(novoTotal);
            }

            if (grupoId != null) {
                lancamento.setTotalParcelas(novoTotal);

                if (novoTotal < originalTotal) {
                    // Diminuir: Remove excedentes
                    repository.deleteByGrupoIdAndParcelaActualGreaterThan(grupoId, novoTotal);
                } else if (novoTotal > originalTotal) {
                    // Aumentar: Cria novos registros baseados na parcela atual (já usa a lógica de âncora)
                    int parcelasParaCriar = novoTotal - originalTotal;
                    List<Lancamento> grupoAtual = repository.findByGrupoId(grupoId);
                    int ultimaParcelaExistente = grupoAtual.stream()
                            .mapToInt(l -> l.getParcelaActual() != null ? l.getParcelaActual() : 0)
                            .max().orElse(originalTotal);

                    int pActual = lancamento.getParcelaActual() != null ? lancamento.getParcelaActual() : 1;
                    LocalDate baseDate = LocalDate.of(lancamento.getAno(), lancamento.getMes(), 1);

                    for (int i = 0; i < parcelasParaCriar; i++) {
                        int nParcela = ultimaParcelaExistente + i + 1;
                        LocalDate nextDate = baseDate.plusMonths(nParcela - pActual);

                        Lancamento nova = toEntity(dto);
                        nova.setGrupoId(grupoId);
                        nova.setParcelaActual(nParcela);
                        nova.setTotalParcelas(novoTotal);
                        nova.setMes(nextDate.getMonthValue());
                        nova.setAno(nextDate.getYear());
                        nova.setDescricao(baseDesc + " (" + nParcela + "/" + novoTotal + ")");
                        repository.save(nova);
                    }
                }

                // SINCRONIZAÇÃO GERAL DO GRUPO (Datas, Descrições, Categorias)
                LocalDate dataAncora = LocalDate.of(lancamento.getAno(), lancamento.getMes(), 1);
                int indexAncora = lancamento.getParcelaActual() != null ? lancamento.getParcelaActual() : 1;

                List<Lancamento> grupoFinal = repository.findByGrupoId(grupoId);
                for (Lancamento l : grupoFinal) {
                    if (!l.getId().equals(lancamento.getId())) {
                        // Sincroniza Metadados
                        l.setTotalParcelas(novoTotal);
                        l.setCategoria(dto.getCategoria());
                        l.setSubcategoria(dto.getSubcategoria());
                        l.setDescricao(baseDesc + " (" + l.getParcelaActual() + "/" + novoTotal + ")");
                        
                        // Sincroniza Datas (Mantendo o intervalo mensal relativo à âncora)
                        int offset = l.getParcelaActual() - indexAncora;
                        LocalDate novaData = dataAncora.plusMonths(offset);
                        l.setMes(novaData.getMonthValue());
                        l.setAno(novaData.getYear());
                    }
                }
            }
        }

        return toDTO(repository.save(lancamento));
    }

    @Transactional
    public void excluir(Long id, boolean excluirProximos) {
        Lancamento lancamento = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lançamento não encontrado: " + id));

        if (excluirProximos && lancamento.getGrupoId() != null && lancamento.getParcelaActual() != null) {
            repository.deleteByGrupoIdAndParcelaActualGreaterThanEqual(
                    lancamento.getGrupoId(),
                    lancamento.getParcelaActual()
            );
        } else {
            repository.deleteById(id);
        }
    }

    // ─── DASHBOARD ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardDTO calcularDashboard(Integer ano) {
        List<BigDecimal> receitasPorMes     = new ArrayList<>();
        List<BigDecimal> gastosPorMes       = new ArrayList<>();
        List<BigDecimal> assinaturasPorMes  = new ArrayList<>();
        List<BigDecimal> saldoPorMes        = new ArrayList<>();

        for (int mes = 1; mes <= 12; mes++) {
            BigDecimal receitas    = repository.sumByAnoAndMesAndCategoria(ano, mes, Categoria.RECEITA);
            // GASTO + GASTO_FIXO somados como "gastos" no gráfico de barras
            BigDecimal gastos      = repository.sumByAnoAndMesAndCategoriaIn(ano, mes,
                    List.of(Categoria.GASTO, Categoria.GASTO_FIXO));
            BigDecimal assinaturas = repository.sumByAnoAndMesAndCategoria(ano, mes, Categoria.ASSINATURA);
            BigDecimal saldo       = receitas.subtract(gastos).subtract(assinaturas);

            receitasPorMes.add(receitas);
            gastosPorMes.add(gastos);
            assinaturasPorMes.add(assinaturas);
            saldoPorMes.add(saldo);
        }

        BigDecimal totalReceitas    = repository.sumByAnoAndCategoria(ano, Categoria.RECEITA);
        BigDecimal totalGastos      = repository.sumByAnoAndCategoriaIn(ano, List.of(Categoria.GASTO, Categoria.GASTO_FIXO));
        BigDecimal totalAssinaturas = repository.sumByAnoAndCategoria(ano, Categoria.ASSINATURA);
        BigDecimal saldoAnual       = totalReceitas.subtract(totalGastos).subtract(totalAssinaturas);

        // Todos os tipos de saída para o gráfico donut e top 5
        List<Lancamento> todasSaidas = new ArrayList<>();
        todasSaidas.addAll(repository.findByAnoAndCategoriaOrderByMesAscSubcategoriaAsc(ano, Categoria.GASTO));
        todasSaidas.addAll(repository.findByAnoAndCategoriaOrderByMesAscSubcategoriaAsc(ano, Categoria.GASTO_FIXO));
        todasSaidas.addAll(repository.findByAnoAndCategoriaOrderByMesAscSubcategoriaAsc(ano, Categoria.ASSINATURA));

        Map<String, BigDecimal> gastosPorSubcategoria = todasSaidas.stream()
                .collect(Collectors.groupingBy(
                        Lancamento::getSubcategoria,
                        Collectors.reducing(BigDecimal.ZERO, Lancamento::getValor, BigDecimal::add)
                ));

        // Top 5 gastos do ano
        List<DashboardDTO.SubcategoriaValor> topGastos = gastosPorSubcategoria.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> new DashboardDTO.SubcategoriaValor(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        return DashboardDTO.builder()
                .ano(ano)
                .totalReceitas(totalReceitas)
                .totalGastos(totalGastos)
                .totalAssinaturas(totalAssinaturas)
                .saldoAnual(saldoAnual)
                .receitasPorMes(receitasPorMes)
                .gastosPorMes(gastosPorMes)
                .assinaturasPorMes(assinaturasPorMes)
                .saldoPorMes(saldoPorMes)
                .gastosPorSubcategoria(gastosPorSubcategoria)
                .topGastos(topGastos)
                .build();
    }

    // ─── MAPEADORES ──────────────────────────────────────────────────────────

    private LancamentoDTO toDTO(Lancamento l) {
        return LancamentoDTO.builder()
                .id(l.getId())
                .descricao(l.getDescricao())
                .categoria(l.getCategoria())
                .subcategoria(l.getSubcategoria())
                .valor(l.getValor())
                .mes(l.getMes())
                .ano(l.getAno())
                .parcelaActual(l.getParcelaActual())
                .totalParcelas(l.getTotalParcelas())
                .grupoId(l.getGrupoId())
                .build();
    }

    private Lancamento toEntity(LancamentoDTO dto) {
        return Lancamento.builder()
                .descricao(dto.getDescricao())
                .categoria(dto.getCategoria())
                .subcategoria(dto.getSubcategoria())
                .valor(dto.getValor())
                .mes(dto.getMes())
                .ano(dto.getAno())
                .parcelaActual(dto.getParcelaActual())
                .totalParcelas(dto.getTotalParcelas())
                .grupoId(dto.getGrupoId())
                .build();
    }

    private String limparDescricao(String desc) {
        if (desc == null) return null;
        // Remove um ou mais sufixos (X/Y) acumulados e espaços extras
        return desc.trim().replaceAll("(\\s*\\(\\d+/\\d+\\))+$", "").trim();
    }
}
