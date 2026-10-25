# Token Savings Analysis

Relatório de auditoria e métricas de economia de tokens do projeto **fcontrol (MyTwoCents)** com base na **Regra das 300 Linhas** e no princípio de **Separação de Preocupações**.

---

## 1. Auditoria Geral do Código (Arquivos > 300 Linhas)

| Arquivo | Linhas Atuais | Tipo de Gargalo | Recomendação de Modularização |
| :--- | :---: | :--- | :--- |
| `src/main/resources/static/js/investments.js` | 2.490 | Monólito Frontend (Carteira) | Dividir em: `inv-state.js`, `inv-modal.js`, `inv-tables.js`, `inv-sniper.js` |
| `src/main/resources/static/css/style.css` | 2.110 | Monólito de Estilos CSS | Separar em módulos via `@import`: `variables.css`, `base.css`, `components.css`, `investments.css`, `chat.css` |
| `src/main/resources/static/index.html` | 1.186 | Monólito HTML (SPA) | Extrair modais e templates para templates/componentes parciais |
| `src/main/java/.../service/InvestimentoService.java` | 1.151 | Monólito de Serviço Backend | Delegar para: `InvestimentoCalculoService`, `InvestimentoProventoService`, `AtivoQueryService` |
| `src/main/java/.../service/AiService.java` | 1.075 | Monólito de IA Backend | Separar prompts em recursos externos e dividir em `AiChatService` e `AiParserService` |
| `src/main/resources/static/js/app.js` | 1.068 | Monólito Frontend (Core SPA) | Separar navegação, estado de filtros e lógica do formulário orçamentário |
| `src/main/resources/static/js/ai-chat.js` | 1.005 | Monólito Frontend (IA) | Dividir em: `chat-ui.js`, `document-parser.js`, `chat-storage.js` |
| `src/main/java/.../MyTwoCentsDesktopApp.java` | 900 | Monólito Desktop JavaFX | Extrair gerenciamento de menus, tray icon e inicialização do WebView para classes auxiliares |
| `src/main/java/.../service/SniperEngineService.java` | 805 | Monólito de Cálculo Backend | Separar `EmergencyBoxCalculator` e `TacticalAllocationCalculator` |
| `src/main/resources/static/js/charts.js` | 726 | Monólito de Gráficos | Isolar configurações de cada tipo de gráfico (Donut, Barras, Histórico) |
| `src/main/java/.../service/CotacaoService.java` | 616 | Serviço de Cotações | Separar `BrapiClient` e `CoinGeckoClient` em adaptadores dedicados |
| `docs/TECHNICAL.md` | 555 *(Antes)* | Monólito de Documentação | **Concluído:** Decomposto em `architecture.md`, `api.md`, `frontend.md` e índice |
| `src/main/java/.../service/LancamentoService.java` | 370 | Serviço Orçamentário | Isolar agregador do dashboard em `DashboardAggregationService` |
| `src/main/java/.../service/ExportService.java` | 360 | Serviço de Exportação | Separar geradores CSV e Excel |
| `src/main/java/.../controller/UpdateController.java` | 350 | Controller de Updates | Extrair chamadas do GitHub Releases para serviço dedicado |
| `src/main/resources/static/js/api.js` | 344 | Cliente HTTP Frontend | Dividir em clientes de domínio (`api-lancamentos.js`, `api-investimentos.js`, etc.) |
| `src/main/java/.../controller/InvestimentoController.java` | 338 | Controller de Investimentos | Segregar endpoints de carteira e endpoints de cálculo sniper |

---

## 2. Refatorações Concluídas & Economia Medida

### Módulo de Documentação Técnica (`docs/TECHNICAL.md`)

A documentação monolítica de 555 linhas cobria backend, frontend, desktop e banco de dados simultaneamente. Foi refatorada em uma arquitetura de tópicos focados:
- `docs/TECHNICAL.md` (Índice enxuto com links e comandos rápidos)
- `docs/architecture.md` (Spring Boot, JavaFX, H2 e Flyway)
- `docs/api.md` (Especificação completa dos contratos REST)
- `docs/frontend.md` (Estrutura da SPA, scripts JS e Design System)

| Métrica | Antes (Monólito) | Depois (Índice Central) | Economia |
| :--- | :--- | :--- | :--- |
| **Tamanho do Arquivo** | ~555 linhas | ~37 linhas | **93.3%** |
| **Custo em Tokens (Índice)** | ~5.000 tokens | ~350 tokens | **93.0%** |
| **Leitura por Tópico Específico** | ~5.000 tokens | ~800 tokens | **84.0%** |

---

## 3. Próximos Passos Recomendados para Máxima Eficiência

1. **Modularização de CSS (`style.css` - 2.110 linhas)**:
   - Dividir em `css/tokens.css`, `css/components.css`, `css/investments.css` e `css/chat.css`.
   - Ganho estimado: **70% de redução de contexto** ao editar componentes específicos de UI.

2. **Modularização do JavaScript de Investimentos (`investments.js` - 2.490 linhas)**:
   - Extrair a lógica do modal de lançamento e do algoritmo de exibição sniper para módulos ES dedicados.
   - Ganho estimado: **75% de economia de contexto** por sessão de desenvolvimento.

3. **Backend Service Decomposition (`InvestimentoService.java` - 1.151 linhas & `AiService.java` - 1.075 linhas)**:
   - Extrair prompts da IA para arquivos `.st` / `.txt` em `resources/prompts/`.
   - Criar serviços especialistas focados com menos de 300 linhas cada.
