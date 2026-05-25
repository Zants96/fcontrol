package br.com.lesnik.mytwocents.repository;

import br.com.lesnik.mytwocents.model.AiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConfigRepository extends JpaRepository<AiConfig, Long> {

    /** Busca a configuração ativa (sempre haverá no máximo uma) */
    Optional<AiConfig> findFirstByOrderByIdDesc();
}
