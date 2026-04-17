package br.com.lesnik.fcontrol.repository;

import br.com.lesnik.fcontrol.model.Categoria;
import br.com.lesnik.fcontrol.model.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    List<Lancamento> findByAnoOrderByMesAscCategoriaAscSubcategoriaAsc(Integer ano);

    List<Lancamento> findByAnoAndMesOrderByCategoriaAscSubcategoriaAsc(Integer ano, Integer mes);

    List<Lancamento> findByAnoAndCategoriaOrderByMesAscSubcategoriaAsc(Integer ano, Categoria categoria);

    List<Lancamento> findByAnoAndMesAndCategoriaOrderBySubcategoriaAsc(Integer ano, Integer mes, Categoria categoria);

    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM Lancamento l WHERE l.ano = :ano AND l.mes = :mes AND l.categoria = :cat")
    BigDecimal sumByAnoAndMesAndCategoria(@Param("ano") Integer ano, @Param("mes") Integer mes, @Param("cat") Categoria cat);

    /** Soma valores de múltiplas categorias num mesmo mês (ex: GASTO + GASTO_FIXO no dashboard) */
    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM Lancamento l WHERE l.ano = :ano AND l.mes = :mes AND l.categoria IN :cats")
    BigDecimal sumByAnoAndMesAndCategoriaIn(@Param("ano") Integer ano, @Param("mes") Integer mes, @Param("cats") List<Categoria> cats);

    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM Lancamento l WHERE l.ano = :ano AND l.categoria = :cat")
    BigDecimal sumByAnoAndCategoria(@Param("ano") Integer ano, @Param("cat") Categoria cat);

    /** Soma valores de múltiplas categorias num ano inteiro */
    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM Lancamento l WHERE l.ano = :ano AND l.categoria IN :cats")
    BigDecimal sumByAnoAndCategoriaIn(@Param("ano") Integer ano, @Param("cats") List<Categoria> cats);

    @Query("SELECT DISTINCT l.subcategoria FROM Lancamento l WHERE l.categoria = :cat ORDER BY l.subcategoria")
    List<String> findDistinctSubcategoriasByCategoria(@Param("cat") Categoria cat);

    boolean existsByAnoAndCategoriaAndSubcategoria(Integer ano, Categoria categoria, String subcategoria);

    @Modifying
    @Query("DELETE FROM Lancamento l WHERE l.valor = 0")
    int deleteAllZeroValue();

    List<Lancamento> findByGrupoId(String grupoId);

    @Modifying
    @Query("DELETE FROM Lancamento l WHERE l.grupoId = :grupoId AND l.parcelaActual > :parcela")
    void deleteByGrupoIdAndParcelaActualGreaterThan(@Param("grupoId") String grupoId, @Param("parcela") Integer parcela);

    @Modifying
    @Query("DELETE FROM Lancamento l WHERE l.grupoId = :grupoId AND l.parcelaActual >= :parcela")
    void deleteByGrupoIdAndParcelaActualGreaterThanEqual(@Param("grupoId") String grupoId, @Param("parcela") Integer parcela);
}
