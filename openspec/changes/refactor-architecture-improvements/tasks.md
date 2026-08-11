# Tasks: Refatoração de Arquitetura e Melhorias Estruturais

- [x] **Task 1: Decompor `MyTwoCentsDesktopApp` em componentes dedicados** <!-- id: 1 -->
  - Criar `DesktopUpdateChecker.java` em `br.com.lesnik.mytwocents.desktop`.
  - Criar `DesktopBackupHandler.java` em `br.com.lesnik.mytwocents.desktop`.
  - Refatorar `MyTwoCentsDesktopApp.java` delegando as operações nativas e enxugando a classe mantendo a forte referência de GC da `JavaBridge`.

- [x] **Task 2: Configurar Flyway DB Migration com Baseline Retrocompatível** <!-- id: 2 -->
  - Adicionar `org.flywaydb:flyway-core` no `pom.xml`.
  - Configurar `spring.flyway.baseline-on-migrate=true` em `application.properties`.
  - Criar `V1__init_schema.sql` em `src/main/resources/db/migration/`.

- [x] **Task 3: Implementar Tratamento Global de Exceções REST** <!-- id: 3 -->
  - Criar `ErrorResponseDTO.java` no pacote `br.com.lesnik.mytwocents.dto`.
  - Criar `GlobalExceptionHandler.java` em `br.com.lesnik.mytwocents.config`.
  - Adicionar validações seguras `@NotNull` / `@Valid` nos endpoints REST.

- [x] **Task 4: Criar Utilitário Reutilizável JS (`utils.js`) no Frontend** <!-- id: 4 -->
  - Criar `src/main/resources/static/js/utils.js`.
  - Incluir `utils.js` em `src/main/resources/static/index.html`.

- [x] **Task 5: Testes e Validação Completa de Não-Quebra** <!-- id: 5 -->
  - Executar os testes unitários do Spring Boot.
  - Verificar compilação JavaFX e integridade do Flyway.
