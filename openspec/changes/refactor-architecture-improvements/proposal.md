# Proposal: Refatoração de Arquitetura e Melhorias Estruturais (fcontrol)

## Why

A análise da estrutura do projeto **MyTwoCents (fcontrol)** identificou 4 pontos críticos de arquitetura que comprometem a manutenibilidade e resiliência:
1. **Desktop App Monolítico**: `MyTwoCentsDesktopApp.java` engloba GUI JavaFX, WebView, gerenciamento do servidor Spring, verificação de atualizações no GitHub API e lógica de diálogo de backup.
2. **Ausência de Migração de Banco**: Dependência do `hibernate ddl-auto=update` sem histórico versionado de DDL para banco H2 local.
3. **Falta de Padronização de Erros REST**: Exceções no backend não tratadas por `@ControllerAdvice` geram respostas sem estrutura legível.
4. **Duplicação de Código no Frontend**: Formatadores de data, moeda e utilitários replicados entre `app.js` e `investments.js`.

## What

Aplicar as 5 melhorias práticas recomendadas com garantia total de retrocompatibilidade:
1. **Decomposição do Desktop App**: Extrair `DesktopUpdateChecker` e `DesktopBackupHandler` desacoplando responsabilidades da classe JavaFX principal.
2. **Flyway DB Migration**: Introduzir Flyway com `baseline-on-migrate=true` e script inicial `V1__init_schema.sql`.
3. **Tratamento Global de Erros REST**: Adicionar `GlobalExceptionHandler` e `ErrorResponseDTO`.
4. **Módulo de Utilitários JS**: Criar `utils.js` e incluí-lo no `index.html` mantendo compatibilidade total com a SPA Vanilla.

## Non-Goals

- Não alterar contratos de rotas REST existentes (`/api/lancamentos`, `/api/investimentos`, etc.).
- Não alterar a interface gráfica nem a marcação dos elementos no `index.html`.
- Não forçar reescrita do frontend para frameworks (React/Vue), preservando a SPA Vanilla HTML5/JS.

## Impact & Compatibility

- **JavaFX JS Bridge**: Preservação estrita dos métodos `saveFile`, `importFile`, `checkUpdates` chamados via JavaScript (`window.javaBridge`).
- **Persistência H2**: Garantia de funcionamento sem perdas para bancos de dados `.mv.db` já existentes via flag de baseline do Flyway.
