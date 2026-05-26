package br.com.lesnik.mytwocents.repository;

import br.com.lesnik.mytwocents.model.Ativo;
import br.com.lesnik.mytwocents.model.TipoAtivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AtivoRepository extends JpaRepository<Ativo, Long> {

    Optional<Ativo> findByTickerIgnoreCase(String ticker);

    List<Ativo> findByAtivoTrueOrderByTipoAtivoAscTickerAsc();

    List<Ativo> findByTipoAtivoAndAtivoTrueOrderByTickerAsc(TipoAtivo tipoAtivo);
}
