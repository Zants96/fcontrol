# Proposal: Otimização do Tamanho do Instalador e Validação de Saúde

## Why

A análise da compilação do instalador identificou dois grandes gargalos de tamanho no binário final:
1. **Módulo Guarda-Chuva `java.se` no `jlink`**: O JRE embutido carrega módulos desnecessários da plataforma Java SE (RMI, Corba, XML) aumentando o tamanho da instalação em ~40MB.
2. **Duplicação de Binários Nativos JavaFX no `pom.xml`**: O JAR empacotava arquivos `.dll` do Windows e `.so` do Linux simultaneamente, inflando a distribuição.

Além disso, é necessário executar a suíte completa de testes para garantir que a aplicação permaneça 100% estável e saudável.

## What

1. **Refatoração do `jlink` nos Scripts de Build**: Declarar apenas os módulos estritamente necessários (`java.base`, `java.desktop`, `java.sql`, `java.net.http`, `java.naming`, `java.instrument`, `java.management`, `jdk.unsupported`, `jdk.crypto.ec`, `jdk.jsobject`) e adicionar as flags `--compress=2` e `--strip-debug`.
2. **Perfis de Binários Nativos no `pom.xml`**: Separar a inclusão dos arquivos nativos JavaFX por perfis Maven para que apenas a plataforma alvo seja empacotada.
3. **Validação de Saúde do Sistema**: Executar a suíte de testes unitários e de integração com o JUnit 5 via Maven (`./mvnw test`).

## Non-Goals

- Não remover funcionalidades da aplicação nem recursos visuais.
- Não alterar a versão do Java (Java 17) ou do Spring Boot.

## Impact & Compatibility

- **Tamanho do Instalador**: Redução estimada de 50MB a 80MB no pacote final distributivo.
- **Confiabilidade**: Garantia de 100% de aprovação nos testes de integração.
