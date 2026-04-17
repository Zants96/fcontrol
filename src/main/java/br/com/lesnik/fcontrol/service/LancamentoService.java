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
        Lancamento lancamento = toEntity(dto);
        return toDTO(repository.save(lancamento));
    }

    @Transactional
    public LancamentoDTO atualizar(Long id, LancamentoDTO dto) {
        Lancamento lancamento = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lançamento não encontrado: " + id));

        lancamento.setDescricao(dto.getDescricao());
        lancamento.setCategoria(dto.getCategoria());
        lancamento.setSubcategoria(dto.getSubcategoria());
        lancamento.setValor(dto.getValor());
        lancamento.setMes(dto.getMes());
        lancamento.setAno(dto.getAno());

        return toDTO(repository.save(lancamento));
    }

    @Transactional
    public void excluir(Long id) {
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("Lançamento não encontrado: " + id);
        }
        repository.deleteById(id);
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
                .build();
    }
}
