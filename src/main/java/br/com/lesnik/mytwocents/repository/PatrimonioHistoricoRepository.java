package br.com.lesnik.mytwocents.repository;

import br.com.lesnik.mytwocents.model.PatrimonioHistorico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatrimonioHistoricoRepository extends JpaRepository<PatrimonioHistorico, Long> {
    Optional<PatrimonioHistorico> findByAnoAndMes(Integer ano, Integer mes);
}
