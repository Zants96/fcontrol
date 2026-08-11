# Proposal: Fortalecimento de Segurança, Validações e Tratamento de Exceções

## Why

A análise do sistema fcontrol revelou 5 fragilidades importantes no tratamento de dados e segurança da aplicação:
1. **Endpoint de Reset Vulnerável**: `POST /api/backup/reset` permite zerar todas as tabelas sem autenticação por senha.
2. **Execução Unsafe de Scripts H2**: Concatenação direta de caminhos temporários em `RUNSCRIPT FROM` no `BackupController`.
3. **Chaves de API em Texto Puro**: Credenciais do Gemini e BrAPI salvas sem criptografia em `ai_config`.
4. **Ausência de Restrições em DTOs**: Aceite de valores negativos ou zerados e tickers sem higienização.
5. **Vazamento de Stacktraces em Respostas HTTP**: Mensagens internas de erro do SQL Engine sendo retornadas nas respostas 500.

## What

Refatorar a segurança e resiliência dos endpoints REST:
1. **Proteção do Endpoint de Reset**: Exigir o cabeçalho `X-Backup-Password` e validar a senha mestra antes de permitir o truncate do banco.
2. **Execução Segura do H2 Restore**: Sanitizar o caminho temporário e utilizar `PreparedStatement` / sintaxe parametrizada para execução dos scripts de backup.
3. **Criptografia de Credenciais de IA**: Utilizar `CryptoService` para encriptar e desencriptar chaves de API salvas no banco.
4. **Validações Estritas com Bean Validation**: Adicionar anotações `@Positive`, `@NotNull`, `@Pattern`, `@Size` em DTOs de lançamentos e investimentos.
5. **Sanitização de Respostas de Erro**: Ocultar detalhes de exceções internas e retornar mensagens seguras ao frontend.

## Non-Goals

- Não alterar os contratos das rotas nem os parâmetros de entrada já existentes.
- Não alterar a interface gráfica do usuário no JavaFX WebView.
- Não exigir login para endpoints de consulta local em leitura.

## Impact & Compatibility

- **Compatibilidade**: Total compatibilidade com a SPA JavaScript.
- **Segurança**: Eliminação do risco de reset indevido da base de dados e vazamento de chaves de API em backups.
- **Integridade**: Garantia de que valores financeiros e quantidades gravados no banco sejam sempre estritamente positivos.
