# Tasks: Eliminação dos 5 Maiores Débitos Técnicos

- [x] **Task 1: Extrair Prompts de IA para arquivos de Recursos Externos** <!-- id: 1 -->
  - Criar diretório `src/main/resources/prompts/`.
  - Mover `FILOSOFIA_INVESTIMENTOS`, `SYSTEM_PROMPT_BASE` e `JSON_STRUCTURE` para `.txt` dedicados.
  - Refatorar `AiService.java` para carregar prompts via `ClassPathResource`.

- [x] **Task 2: Habilitar Caching e Resiliência em `CotacaoService`** <!-- id: 2 -->
  - Habilitar `@EnableCaching` no Spring Boot.
  - Anotar métodos de busca de cotação em `CotacaoService.java` com `@Cacheable`.

- [x] **Task 3: Implementar Store Centralizada de Estado no Frontend (`store.js`)** <!-- id: 3 -->
  - Criar `src/main/resources/static/js/store.js` com suporte a estado e listeners (pub/sub).
  - Incluir `store.js` no `index.html`.

- [x] **Task 4: Restringir `InvestigateDatabase` ao Profile `dev`** <!-- id: 4 -->
  - Anotar `InvestigateDatabase.java` com `@Profile("dev")`.

- [x] **Task 5: Teste e Validação da Refatoração de Débitos Técnicos** <!-- id: 5 -->
  - Compilar e validar a execução sem regresso de funcionalidades.
