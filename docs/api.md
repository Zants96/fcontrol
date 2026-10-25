# MyTwoCents – Referência de Endpoints da API REST

Base URL padrão: `http://localhost:8195/api` (ou porta configurada).

---

## 1. Lançamentos Orçamentários (`/api/lancamentos`)

- `GET /api/lancamentos?ano={ano}&mes={mes}&categoria={categoria}`: Lista lançamentos com filtros opcionais.
- `POST /api/lancamentos`: Cria lançamento (`LancamentoDTO`).
- `PUT /api/lancamentos/{id}`: Atualiza lançamento existente.
- `DELETE /api/lancamentos/{id}`: Remove lançamento.

---

## 2. Dashboard Orçamentário (`/api/dashboard`)

- `GET /api/dashboard?ano={ano}`: Retorna totais anuais, despesas/receitas por mês e top gastos (`DashboardDTO`).

---

## 3. Investimentos (`/api/investimentos`)

- `GET /api/investimentos?tipo={tipo}`: Lista ativos da carteira (com cotação e lucro consolidado).
- `GET /api/investimentos/dashboard`: Resumo do portfólio (patrimônio, rentabilidade, alocação real vs meta).
- `POST /api/investimentos/lancamentos`: Registra operação de COMPRA, VENDA ou DIVIDENDO (`InvestimentoLancamentoDTO`).
- `PUT /api/investimentos/lancamentos/{id}`: Atualiza operação de investimento.
- `DELETE /api/investimentos/lancamentos/{id}`: Remove operação e recalcula preço médio.
- `GET /api/investimentos/ativos/{id}/historico`: Histórico de lançamentos e evolução do ativo.
- `POST /api/investimentos/cotacoes/atualizar`: Força atualização de cotações via BrAPI/CoinGecko.
- `POST /api/investimentos/sniper/calcular`: Executa algoritmo de rebalanceamento e sugere aportes ideais.

---

## 4. Assistente IA (`/api/ai`)

- `POST /api/ai/chat`: Envia mensagem para o consultor financeiro IA com histórico e contexto do portfólio.
- `POST /api/ai/parse`: Processa texto não estruturado (faturas, extratos B3, notas) e retorna itens estruturados.
- `GET /api/ai/config`: Obtém parâmetros de IA e chaves configuradas.
- `POST /api/ai/config`: Salva parâmetros de IA, metas e chaves.

---

## 5. Backup & Atualizações

- `GET /api/backup/export`: Gera arquivo de backup SQLite/JSON compactado.
- `POST /api/backup/import`: Restaura dados a partir de arquivo de backup.
- `GET /api/update/check`: Consulta novas versões disponíveis no GitHub Releases.
- `POST /api/update/apply`: Executa download e script de atualização da aplicação.
