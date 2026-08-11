# Design Document: Refatoração de Arquitetura e Melhorias Estruturais

## Architectural Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    JavaFX Desktop App                       │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │                  MyTwoCentsDesktopApp                   │ │
│ └──────────┬───────────────────────────────┬──────────────┘ │
│            │ Delegate                      │ Delegate       │
│            ▼                               ▼                │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │ DesktopBackupHandler │       │ DesktopUpdateChecker │     │
│ └──────────────────────┘       └──────────────────────┘     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Backend                      │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │ GlobalExceptionHandler│      │  Flyway Migration    │     │
│ └──────────┬───────────┘       └──────────┬───────────┘     │
│            │ JSON Error DTO               │ V1__init.sql    │
│            ▼                              ▼                 │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │   REST Controllers   │       │   H2 Local DB File   │     │
│ └──────────────────────┘       └──────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

## Detailed Design Decisions

### 1. Desacoplamento do Launcher Desktop
- `DesktopUpdateChecker.java`: Gerencia requisições assíncronas à API de releases do GitHub (`/repos/.../releases/latest`), parseia a versão e notifica o usuário via diálogo do JavaFX `Platform.runLater`.
- `DesktopBackupHandler.java`: Concentra os diálogos `FileChooser` e `TextInputDialog` para requisições multipart HTTP `/api/backup/export` e `/api/backup/import`.
- `MyTwoCentsDesktopApp.java`: Reduzido de 1000+ linhas para foco exclusivo em inicializar o Spring Boot Context e carregar a `WebView`.

### 2. Versionamento com Flyway
- Dependência `flyway-core` no Maven.
- `src/main/resources/db/migration/V1__init_schema.sql` definindo as tabelas `lancamentos`, `investimentos`, `cotacoes`, `ativos` com sintaxe H2 SQL.
- Propriedade `spring.flyway.baseline-on-migrate=true` em `application.properties`.

### 3. Tratamento de Exceções REST (`GlobalExceptionHandler`)
- `@RestControllerAdvice` registrando manipuladores para:
  - `@ExceptionHandler(MethodArgumentNotValidException.class)` → 400 Bad Request com lista de campos inválidos.
  - `@ExceptionHandler(IllegalArgumentException.class)` → 400 Bad Request.
  - `@ExceptionHandler(EntityNotFoundException.class)` → 404 Not Found.
  - `@ExceptionHandler(Exception.class)` → 500 Internal Server Error com log limpo.

### 4. Modularização dos Helpers Frontend (`utils.js`)
- Criação de `static/js/utils.js` encapsulando:
  - `formatCurrency(val)` (Intl.NumberFormat pt-BR).
  - `formatDate(dateStr)` (formatador amigável de datas).
  - `safeParseFloat(val)` (evita NaN em entradas do usuário).
