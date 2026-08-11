package br.com.lesnik.mytwocents;

import br.com.lesnik.mytwocents.model.Categoria;
import br.com.lesnik.mytwocents.model.Lancamento;
import br.com.lesnik.mytwocents.repository.LancamentoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Profile("dev")
@Component
public class InvestigateDatabase implements CommandLineRunner {

    private final LancamentoRepository repository;

    public InvestigateDatabase(LancamentoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== SYSTEM INVESTIGATION: START ===");
        List<Lancamento> transferencias = repository.findAll().stream()
                .filter(l -> l.getCategoria() == Categoria.TRANSFERENCIA)
                .toList();
        System.out.println("Encontrados " + transferencias.size() + " lançamentos de categoria TRANSFERENCIA:");
        for (Lancamento l : transferencias) {
            System.out.printf("ID: %d | Desc: %s | Sub: %s | Valor: %s | Mes: %d | Ano: %d%n",
                    l.getId(), l.getDescricao(), l.getSubcategoria(), l.getValor(), l.getMes(), l.getAno());
        }
        System.out.println("=== SYSTEM INVESTIGATION: END ===");
    }
}
