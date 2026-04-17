package br.com.lesnik.fcontrol.config;

import br.com.lesnik.fcontrol.repository.LancamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ao iniciar, remove registros com valor zero que possam ter sido gerados
 * pela versão anterior do DataInitializer (auto-população das subcategorias).
 * Após limpar, a aplicação fica sem dados pré-preenchidos — o usuário
 * adiciona apenas os lançamentos que quiser.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final LancamentoRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        int removed = repository.deleteAllZeroValue();
        if (removed > 0) {
            log.info("Limpeza: {} registro(s) com valor zero removido(s).", removed);
        }
        log.info("Banco de dados pronto. Total de lançamentos: {}", repository.count());
    }
}
