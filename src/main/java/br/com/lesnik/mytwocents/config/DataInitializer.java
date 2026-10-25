package br.com.lesnik.mytwocents.config;

import br.com.lesnik.mytwocents.model.CategoriaTatica;
import br.com.lesnik.mytwocents.model.TipoAtivo;
import br.com.lesnik.mytwocents.repository.AtivoRepository;
import br.com.lesnik.mytwocents.repository.LancamentoRepository;
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
    private final AtivoRepository ativoRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int removed = repository.deleteAllZeroValue();
        if (removed > 0) {
            log.info("Limpeza: {} registro(s) com valor zero removido(s).", removed);
        }

        // Migração de dados legados para a nova categoria TRANSFERENCIA
        int migratedInvestimentos = repository.migrateInvestimentosToTransferencia();
        if (migratedInvestimentos > 0) {
            log.info("Migração: {} lançamento(s) de Investimentos atualizados para TRANSFERENCIA.", migratedInvestimentos);
        }

        int migratedResgates = repository.migrateResgatesToReceita();
        if (migratedResgates > 0) {
            log.info("Migração: {} lançamento(s) de Resgate de Investimentos atualizados para RECEITA.", migratedResgates);
        }

        // Garante que ETFs de Cripto (ABTC11, HASH11, etc.) sejam tratados como CRIPTO e CRESCIMENTO
        for (String ticker : br.com.lesnik.mytwocents.util.CryptoEtfUtils.getCryptoEtfs()) {
            ativoRepository.findByTickerIgnoreCase(ticker).ifPresent(etf -> {
                boolean updated = false;
                if (etf.getTipoAtivo() != TipoAtivo.CRIPTO) {
                    etf.setTipoAtivo(TipoAtivo.CRIPTO);
                    updated = true;
                }
                if (etf.getCategoriaTatica() != CategoriaTatica.CRESCIMENTO) {
                    etf.setCategoriaTatica(CategoriaTatica.CRESCIMENTO);
                    updated = true;
                }
                if (updated) {
                    ativoRepository.save(etf);
                    log.info("Ativo {} atualizado para TipoAtivo.CRIPTO e CategoriaTatica.CRESCIMENTO.", ticker);
                }
            });
        }

        log.info("Banco de dados pronto. Total de lançamentos: {}", repository.count());
    }
}
