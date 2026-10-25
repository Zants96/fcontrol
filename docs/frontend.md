# MyTwoCents – Arquitetura do Frontend

Documentação da interface Single Page Application (SPA), estrutura de scripts e design system.

---

## 1. Estrutura da SPA (`index.html`)

A aplicação roda em uma única página HTML sem recarregamento:
- **Sidebar (`<aside class="sidebar">`)**: Navegação entre views e status de versão/backup.
- **Topbar (`<header class="topbar">`)**: Seletor global de ano, botão de atualização de cotações e resumo do mês.
- **Views (`<section id="view-*">`)**:
  - `view-dashboard`: Métricas orçamentárias anuais e gráficos gerais.
  - `view-tabela`: Tabela orçamentária detalhada por mês/categoria.
  - `view-investimentos`: Painel de portfólio, metas de alocação, histórico de ativos e motor Sniper.
- **Modais & Overlays**:
  - `inv-modal-overlay`: Modal para novos aportes e dividendos.
  - `ai-chat-panel`: Assistente virtual flutuante com chat e analisador de faturas.
  - `inv-historico-modal`: Visualização e edição de lançamentos por ativo.

---

## 2. Módulos JavaScript (`src/main/resources/static/js/`)

- **`app.js`**: Estado global da SPA, roteamento interno entre views orçamentárias e ciclo de vida do dashboard financeiro.
- **`investments.js`**: Gestão do portfólio de investimentos, tabelas por classe de ativo, modal de operações e integração com o motor Sniper.
- **`ai-chat.js`**: Controle do chat IA (Gemini), streaming/renderização de Markdown e detecção/parse de faturas e comprovantes.
- **`charts.js`**: Instanciação e ciclo de vida dos gráficos Chart.js (rosca, barras agrupadas, linha de evolução e alocação tática).
- **`api.js`**: Camada centralizada de chamadas HTTP (`fetch`) com tratamento de exceções.
- **`store.js` & `utils.js`**: Utilitários para formatação de moeda, datas e manipulação de DOM.

---

## 3. Design System (`src/main/resources/static/css/style.css`)

- **Tema**: Dark mode profissional com paleta em tons escuros e acentos esmeralda/ciano.
- **Tipografia**: Família `Inter` (Google Fonts).
- **Componentes**: Botões interativos, badges de status, cards com gradientes sutis, tabelas com scroll virtual e animações de feedback.
