# Design Document: Fortalecimento de Segurança e Validação

## Architectural Overview

```
┌─────────────────────────────────────────────────────────────┐
│                       REST API Layer                        │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │   BackupController   │       │   Controllers REST   │     │
│ └──────────┬───────────┘       └──────────┬───────────┘     │
│            │ Valida Senha                 │ Valida @Valid   │
│            ▼                              ▼                 │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │    CryptoService     │       │GlobalExceptionHandler│     │
│ └──────────┬───────────┘       └──────────────────────┘     │
│            │ Encripta Keys                                  │
│            ▼                                                │
│ ┌──────────────────────┐                                    │
│ │   H2 Local Database  │                                    │
│ └──────────────────────┘                                    │
└─────────────────────────────────────────────────────────────┘
```

## Detailed Design Decisions

### 1. Proteção de Endpoints de Backup e Reset
- `BackupController.resetDatabase(@RequestHeader("X-Backup-Password") String password)`: Valida a senha solicitando descriptografia de teste ou batendo contra a credencial da sessão desktop antes de executar os truncates.
- `BackupController.importBackup`: Sanitiza o caminho temporário gerado e garante deleção segura no bloco `finally`.

### 2. Criptografia de Credenciais de IA (`CryptoService`)
- Atualização em `AiConfig`: Salva a `apiKey` e `brapiToken` criptografados via AES na persistência JPA.
- O `AiService` desativa o print ou vazamento em logs de qualquer chave de API nas chamadas externas.

### 3. Validações com Jakarta Bean Validation
- `LancamentoDTO`:
  - `@NotNull @Positive BigDecimal valor`
  - `@NotBlank @Size(max=255) String descricao`
  - `@Min(1) @Max(12) Integer mes`
- `InvestimentoLancamentoDTO`:
  - `@NotNull @Positive BigDecimal quantidade`
  - `@NotNull @Positive BigDecimal precoUnitario`
  - `@Pattern(regexp = "^[A-Z0-9]{2,12}$") String ticker`

### 4. Sanitização de Respostas HTTP de Erro
- Em `BackupController` e `GlobalExceptionHandler`, remoção completa de mensagens cruas como `e.getMessage()` que exponham exceções SQL para o cliente HTTP.
