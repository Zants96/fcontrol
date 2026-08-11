# Design Document: Otimização de Tamanho do Instalador e Testes de Saúde

## Architectural Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Build & Packaging Layer                  │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │   build-installer    │──────▶│    Custom JRE        │     │
│ │  (Linux / Windows)   │       │ (jlink compressed)   │     │
│ └──────────────────────┘       └──────────────────────┘     │
└───────────────────────────┬─────────────────────────────────┘
                            │ Multi-Platform Profiles
┌───────────────────────────▼─────────────────────────────────┐
│                    Maven Dependency Tree                    │
│ ┌──────────────────────┐       ┌──────────────────────┐     │
│ │  platform-linux      │       │    platform-win      │     │
│ │ (Linux .so only)     │       │  (Windows .dll only) │     │
│ └──────────────────────┘       └──────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

## Detailed Design Decisions

### 1. Lista Enxuta de Módulos no `jlink`
Substituir `java.se` por:
- `java.base`
- `java.desktop`
- `java.sql`
- `java.net.http`
- `java.naming`
- `java.instrument`
- `java.management`
- `jdk.unsupported`
- `jdk.crypto.ec`
- `jdk.jsobject`

E adicionar as flags:
- `--strip-debug`
- `--no-header-files`
- `--no-man-pages`
- `--compress=2`

### 2. Perfis Maven em `pom.xml`
- Perfil `platform-linux` (ativado em OS Linux ou via `-Pplatform-linux`) incluindo classifiers `linux`.
- Perfil `platform-win` (ativado em OS Windows ou via `-Pplatform-win`) incluindo classifiers `win`.

### 3. Suíte de Testes de Saúde
Execução de `mvn test` cobrindo:
- `LancamentoServiceTest`
- `CotacaoServiceTest`
- `CryptoServiceTest`
- `ExportServiceTest`
- `LancamentoControllerTest`
- `BackupIntegrationTest`
