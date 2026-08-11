# Proposal: Eliminação dos 5 Maiores Débitos Técnicos (fcontrol)

## Why

A avaliação arquitetural do projeto identificou 5 grandes débitos técnicos que reduzem a manutenibilidade, desaceleram o desenvolvimento e geram potenciais riscos de estabilidade:
1. **AiService Monolítico**: Prompts extensos hardcoded em Java e RestTemplate síncrono acoplado sem suporte limpo a multi-provedores.
2. **Frontend Imperativo sem Estado**: `investments.js` e `app.js` manipulando DOM diretamente sem um repositório centralizado de estado.
3. **Cálculos Financeiros Duplicados**: Lógicas de preço médio e rentabilidade recalculadas separadamente no Java e no JS.
4. **Cotações Síncronas sem Cache**: `CotacaoService` realizando requisições externas bloqueantes sem cache, podendo congelar a WebView JavaFX.
5. **Código de Debug em Produção**: `InvestigateDatabase.java` executando código de depuração na inicialização em produção.

## What

Refatorar os 5 débitos de forma organizada e segura:
1. **Templates de Prompts Extensíveis**: Mover os prompts para `src/main/resources/prompts/` e abstrair a comunicação com IA.
2. **Gerenciador de Estado no Frontend**: Criar `store.js` para manter o estado da aplicação desacoplado da manipulação direta do DOM.
3. **Backend como Fonte Única da Verdade**: Consolidar os cálculos financeiros no `InvestimentoService` e retornar DTOs imutáveis.
4. **Cache & Assincronismo em Cotações**: Habilitar `@EnableCaching` e buscar cotações com tempo de expiração para evitar chamadas redundantes.
5. **Isolamento de Debug por Profile**: Restringir `InvestigateDatabase.java` ao profile `@Profile("dev")`.

## Non-Goals

- Não alterar as regras de negócio dos cálculos de rentabilidade ou regras de investimento.
- Não alterar a interface gráfica visual vista pelo usuário.
- Não introduzir frameworks pesados de frontend (React/Angular), mantendo a leveza do ecossistema Vanilla JS.

## Impact & Compatibility

- **Transparência Visual**: A experiência do usuário permanece rigorosamente idêntica.
- **Desempenho**: Redução drástica nas chamadas a APIs de cotações externas e eliminação de travamentos da WebView.
- **Manutenibilidade**: Prompts e estado frontend claramente isolados em arquivos específicos.
