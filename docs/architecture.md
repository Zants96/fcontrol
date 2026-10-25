# MyTwoCents – Arquitetura do Sistema

Documentação da arquitetura estrutural do backend Spring Boot, camada desktop JavaFX e banco de dados H2.

---

## 1. Backend Spring Boot

O backend adota o padrão em camadas clássico:

```
Controller  →  Service  →  Repository  →  Database
   (API)      (regras)      (acesso)       (H2)
```

### Ponto de Entrada
- **`MyTwoCentsApplication.java`**: Inicializa o Spring Boot, Tomcat embutido e escaneia componentes (`@SpringBootApplication`, `@EnableCaching`).

### Camada de Modelo (Entidades & Enums)
- **`model/Categoria.java`**: Enum (`RECEITA`, `GASTO`, `ASSINATURA`, `TRANSFERENCIA`).
- **`model/Lancamento.java`**: Entidade JPA principal para transações financeiras.
- **`model/Ativo.java`**: Entidade JPA representando ativos em carteira (ações, FIIs, ETFs, cripto, etc.).
- **`model/InvestimentoLancamento.java`**: Operações de aporte, venda ou recebimento de dividendos.
- **`model/AiConfig.java`**: Configurações de API keys (Gemini, BrAPI, CoinGecko) e parâmetros do usuário.

### Camada de Repositório
- **`repository/LancamentoRepository.java`**: Operações no banco para lançamentos e agregações do dashboard.
- **`repository/AtivoRepository.java`**: Gestão de ativos e filtros por tipo e status.
- **`repository/InvestimentoLancamentoRepository.java`**: Histórico transacional de investimentos.
- **`repository/AiConfigRepository.java`**: Persistência de configurações.

### Camada de Serviços
- **`LancamentoService`**: Regras de negócio orçamentárias e agregações do dashboard.
- **`InvestimentoService`**: Gestão de portfólio, posição consolidada, preço médio e proventos.
- **`SniperEngineService`**: Algoritmo de rebalanceamento tático e recomendação de aportes inteligentes.
- **`CotacaoService`**: Integração de cotações com BrAPI (B3) e CoinGecko (Criptos) com cache em memória.
- **`AiService`**: Integração com Google Gemini para chat financeiro, parsing de faturas/notas e insights.

---

## 2. Camada Desktop (JavaFX)

- **`MyTwoCentsDesktopApp.java`**: Aplicação Desktop que embute o servidor Spring Boot e exibe a SPA em um `WebView` JavaFX integrado com Tray Icon.
- **`DesktopBackupHandler.java`**: Exportação e importação de backups seguros.
- **`DesktopUpdateChecker.java`**: Verificação assíncrona de novas versões no GitHub Releases.

---

## 3. Banco de Dados & Migrações

- **Engine**: H2 Database em arquivo local (`~/.mytwocents/data.mv.db`), persistente e portátil.
- **Flyway**: Gerenciador de migrações automáticas versionadas (`classpath:db/migration`):
  - `V1__init_schema.sql`: Tabelas iniciais (`lancamento`, `ativo`, `investimento_lancamento`, `ai_config`).
  - `V2__add_sniper_mode_fields.sql`: Campos para estratégia Sniper e reserva de emergência.
  - `V3__add_taxa_selic_ipca.sql`: Suporte a taxas macroeconômicas.
  - `V4__add_taxa_cdi.sql`: Suporte à taxa CDI.
  - `V5__abtc11_cripto.sql`: Classificação de ETF ABTC11 como criptomoeda.
