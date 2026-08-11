# Design Document: Resolução dos 5 Débitos Técnicos

## Architectural Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend JS Layer                        │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │       store.js       │──────▶│    UI Renderers      │     │
│ │   (Central State)    │       │ (app.js / invest.js) │     │
│ └──────────────────────┘       └──────────────────────┘     │
└───────────────────────────┬─────────────────────────────────┘
                            │ REST APIs
┌───────────────────────────▼─────────────────────────────────┐
│                    Spring Boot Backend                      │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │      AiService       │       │    CotacaoService    │     │
│ │ (Resource Templates) │       │ (@Cacheable / Async) │     │
│ └──────────┬───────────┘       └──────────┬───────────┘     │
│            │                              │                 │
│            ▼                              ▼                 │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │ src/resources/prompts│       │ External APIs Cache  │     │
│ └──────────────────────┘       └──────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

## Detailed Design Decisions

### 1. Template-Based Prompt Loading
- Prompts extraídos para:
  - `src/main/resources/prompts/filosofia.txt`
  - `src/main/resources/prompts/system_prompt_base.txt`
  - `src/main/resources/prompts/json_structure.txt`
- `AiService` utiliza `ResourceLoader` do Spring ou `ClassPathResource` para leitura limpa com cache em memória na subida da aplicação.

### 2. Caching de Cotações com Spring Cache
- Adicionada anotação `@EnableCaching` na classe principal Spring (`MyTwoCentsApplication`).
- Métodos de busca de cotações (`CotacaoService.obterCotacao`, `obterSelic`, `obterIpca`) anotados com `@Cacheable(value = "cotacoes", key = "#ticker")`.

### 3. Store de Estado no Frontend (`store.js`)
- Criado `src/main/resources/static/js/store.js` implementando o padrão Publish/Subscribe (Observer).
- Mantém `state = { lancamentos: [], ativos: [], dashboard: null }`.
- Componentes assinam mudanças via `AppStore.subscribe(state => renderUI(state))`.

### 4. Condicional do Script de Inspeção
- `InvestigateDatabase.java` anotado com `@Component` e `@Profile("dev")`.
- Impede a saída no `stdout` durante execuções de produção.
