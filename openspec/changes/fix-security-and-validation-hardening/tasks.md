# Tasks: Fortalecimento de Segurança e Validação de Dados

- [x] **Task 1: Autenticar e Proteger o Endpoint de Reset de Banco de Dados** <!-- id: 1 -->
  - Exigir o cabeçalho `X-Backup-Password` no `POST /api/backup/reset`.
  - Validar a senha antes de executar qualquer limpeza de tabelas.

- [x] **Task 2: Sanitizar Manipulação de Arquivos e Scripts H2 no `BackupController`** <!-- id: 2 -->
  - Garantir a remoção segura de arquivos temporários de backup em blocos `finally`.
  - Ocultar detalhes de exceções internas SQL nas respostas HTTP do `BackupController`.

- [x] **Task 3: Criptografar Chaves de API Salvas na Entidade `AiConfig`** <!-- id: 3 -->
  - Criptografar `apiKey` e `brapiToken` via `CryptoService` ao salvar em `AiConfig`.
  - Descriptografar em memória no momento das chamadas de API externas.

- [x] **Task 4: Adicionar Validações Rigorosas de Entrada nos DTOs** <!-- id: 4 -->
  - Adicionar `@Positive`, `@NotNull`, `@NotBlank`, `@Size` e `@Pattern` em `LancamentoDTO`, `InvestimentoLancamentoDTO` e `AtivoDTO`.
  - Adicionar `@Valid` nos endpoints dos controllers REST.

- [x] **Task 5: Teste e Validação da Segurança e Validação de Dados** <!-- id: 5 -->
  - Testar envio de payloads inválidos e validar respostas 400 limpas.
  - Verificar proteção contra resets não autorizados.
