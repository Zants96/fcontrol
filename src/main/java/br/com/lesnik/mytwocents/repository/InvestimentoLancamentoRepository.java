package br.com.lesnik.mytwocents.repository;

import br.com.lesnik.mytwocents.model.InvestimentoLancamento;
import br.com.lesnik.mytwocents.model.TipoOperacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestimentoLancamentoRepository extends JpaRepository<InvestimentoLancamento, Long> {

    List<InvestimentoLancamento> findByAtivoIdOrderByDataDesc(Long ativoId);

    List<InvestimentoLancamento> findByTipoOperacaoOrderByDataDesc(TipoOperacao tipoOperacao);

    List<InvestimentoLancamento> findAllByOrderByDataDesc();

    @Query("SELECT il FROM InvestimentoLancamento il JOIN FETCH il.ativo ORDER BY il.data DESC")
    List<InvestimentoLancamento> findAllWithAtivoByOrderByDataDesc();
}
