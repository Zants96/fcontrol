# Plano de Implementação: Módulo Sniper Mode & Engine de Alocação Tática no fcontrol

Este documento apresenta a adaptação do **Sniper Mode e Engine de Alocação Tática** (definido originalmente em `implementation_plan_sniper-mode.md`) para ser implementado **diretamente dentro da estrutura e aba de Investimentos existente no fcontrol (My Two Cents)**, aproveitando toda a arquitetura Java 21 / Spring Boot, Flyway, H2/PostgreSQL e a interface SPA Vanilla JS do projeto.

---

## 1. Visão Geral da Integração

O módulo adicionará inteligência de rebalanceamento, gestão de risco de caixa de emergência e matriz tática de compra/venda por desvio de preço médio na aba de **Investimentos** existente no `fcontrol`.

```
 +-------------------------------------------------------------------------+
 |                     Aba Investimentos (Frontend SPA)                     |
 |  - Dashboard Patrimonial (Existente)                                    |
 |  - 🛡️ Cadeado de Segurança (Reserva de Emergência) (NOVO)               |
 |  - 🎯 Matriz Tática / Sniper Mode (Gatilhos Compra/Venda) (NOVO)         |
 |  - 💡 Simulador de Aporte Inteligente Dividido (Smart Split) (NOVO)      |
 +------------------------------------+------------------------------------+
                                      | REST API (JSON)
                                      v
 +------------------------------------+------------------------------------+
 |                  Backend Spring Boot (fcontrol)                        |
 |  - InvestimentoController (Extendido)                                   |
 |  - SniperEngineService (NOVO):                                          |
 |      * Cadeado de Segurança (Emergency Lock)                            |
 |      * Aporte Proporcional por Déficit                                  |
 |      * Matriz de Escalonamento Tático (Cíclico vs Estrutural)            |
 +------------------------------------+------------------------------------+
                                      | JPA / Hibernate / Flyway
                                      v
 +------------------------------------+------------------------------------+
 |                      Banco de Dados Relacional                          |
 |  - Tabela `ativo` (Novas colunas: categoria_tatica, is_ciclico, etc.)   |
 |  - Tabela `ai_config` ou `system_config` (Metas Globais)                 |
 +-------------------------------------------------------------------------+
```

---

## 2. Modelagem de Dados & Banco de Dados

### 2.1 Alterações na Entidade `Ativo` e Tabela `ativo`
Criar a migration Flyway `V14__add_sniper_mode_fields.sql`:

```sql
ALTER TABLE ativo ADD COLUMN categoria_tatica VARCHAR(30) DEFAULT 'RENDA';
ALTER TABLE ativo ADD COLUMN is_ciclico BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ativo ADD COLUMN is_estrutural BOOLEAN NOT NULL DEFAULT TRUE;
```

#### Novas Categorias Táticas (`CategoriaTatica` Enum):
- `SEGURANCA` (Verde Esmeralda - #10b981)
- `RENDA` (Azul Safira - #3b82f6)
- `CRESCIMENTO` (Roxo Violeta - #8b5cf6)
- `GLOBAL` (Laranja Âmbar - #f59e0b)
- `PREVIDENCIA` (Rosa Rosé - #ec4899)

#### Novas Atributos no `Ativo.java`:
```java
@Enumerated(EnumType.STRING)
@Column(name = "categoria_tatica", length = 30)
private CategoriaTatica categoriaTatica;

@Column(name = "is_ciclico", nullable = false)
private boolean ciclico;

@Column(name = "is_estrutural", nullable = false)
private boolean estrutural;
```

---

## 3. Motores de Decisão Matemática (SniperEngineService)

### 3.1 Cadeado de Segurança (Emergency Lock)
Garante a saúde financeira básica antes de permitir aportes em renda variável/ativos de risco.

- **Fórmula**:
  $$\text{isLocked} = (\text{TotalSeguranca} < \text{EmergencyBoxTarget}) \lor (\text{PctSeguranca} < 25.0\%)$$
- **Regra**: Se `isLocked == true`, 100% dos novos aportes (até atingir o déficit da reserva) são direcionados para o ativo de Segurança principal (ex: `CDB-ITAU-POS-1` ou Tesouro Selic).

### 3.2 Aporte Inteligente Dividido (Smart Split Aporte Engine)
Calcula a distribuição proporcional do valor informado para o aporte ($V_{\text{aporte}}$):

1. **Patrimônio Alvo Total**: $P_{\text{futuro}} = P_{\text{atual}} + V_{\text{aporte}}$
2. **Meta em R\$ de cada Ativo ($i$)**:
   $$T_i = P_{\text{futuro}} \times \left(\frac{\text{metaPercent}_i}{100}\right)$$
3. **Déficit Individual ($D_i$)**:
   $$D_i = \max(0, T_i - (\text{quantidade}_i \times \text{precoAtual}_i))$$
4. **Alocação Proporcional ($A_i$)**:
   $$A_i = \min\left(D_i, V_{\text{disponivel}} \times \frac{D_i}{\sum D_k}\right)$$

### 3.3 Matriz de Escalonamento Tático (Sniper Mode)
Disparado para ativos configurados com `is_ciclico = true`:

$$\text{Variação \%} = \frac{\text{PreçoAtual} - \text{PreçoMédio}}{\text{PreçoMédio}} \times 100$$

- **Gatilhos de Compra (Quedas)**:
  - `-20%` a `-29%`: Gatilho Nível 1 (+10% da quantidade alvo)
  - `-30%` a `-49%`: Gatilho Nível 2 (+30% da quantidade alvo)
  - `≤ -50%`: Gatilho Nível 3 (+50% da quantidade alvo)
- **Gatilhos de Venda (Altas)** *(Disponível se `is_estrutural = false`)*:
  - `+30%` a `+39%`: Venda parcial de 10% da posição
  - `+40%` a `+49%`: Venda parcial de 20% da posição
  - `+50%` a `+59%`: Venda parcial de 30% da posição
  - `+60%` a `+99%`: Venda parcial de 40% da posição
  - `≥ +100%`: Sugestão de realização total de lucro / Zerar Posição.

---

## 4. Endpoints REST API (InvestimentoController)

| Método | Rota | Descrição |
| :--- | :--- | :--- |
| `POST` | `/api/investimentos/aporte/calcular` | Recebe `{ "valorAporte": 1500.00 }` e retorna a lista de compras/aportes sugeridos. |
| `GET` | `/api/investimentos/tactical-opportunities` | Lista todas as oportunidades táticas ativas no momento (compras/vendas). |
| `GET` | `/api/investimentos/sniper-overview` | Retorna o resumo consolidado: status do Cadeado de Segurança, metas vs alocações. |
| `PUT` | `/api/investimentos/ativos/{id}/tatica` | Atualiza `categoriaTatica`, `ciclico` e `estrutural` de um ativo. |

---

## 5. Interface do Usuário (Aba de Investimentos)

Aba `Investimentos` no SPA `index.html` e `app.js` receberá novas seções/modais:

1. **Card de Cadeado de Segurança (Topo da Aba)**:
   - Alerta visual destacando o status da Reserva de Emergência (Liberado / Trancado).
   - Barra de progresso para a Meta de Segurança.

2. **Aba/Sub-seção: 🎯 Sniper Mode & Oportunidades Táticas**:
   - Cards com alertas de ativos cíclicos em zona de oportunidade (Exemplo: *BBAS3 caiu -22% do Preço Médio -> Sugestão: Compra Escalonada Nível 1*).
   - Botão **"Executar Lançamento"** que já abre o modal de compra pré-preenchido.

3. **Calculadora de Aporte Inteligente**:
   - Input monetário com máscara BRL (`R$`).
   - Tabela detalhada sugerindo a distribuição por ativo com cotas estimadas e % do aporte total.
   - Botão **"Confirmar Aportes em Lote"** para registrar os lançamentos de compra automaticamente.

4. **Badges de Categoria & Comportamento**:
   - Badges coloridos ao lado dos tickers de cada ativo indicando se é *Cíclico / Estrutural* e sua *Categoria Tática*.

---

## 6. Plano de Testes e Validação

1. **Testes Unitários Backend**:
   - `SniperEngineServiceTest`: Validar o cálculo do Smart Split Aporte para carteiras balanceadas e desbalanceadas.
   - Validar se o `Emergency Lock` intercepta corretamente aportes quando a reserva está abaixo do teto.
   - Validar disparos da Matriz de Escalonamento para variações positivas e negativas.
2. **Testes de Integração API**:
   - Testar requisições em `/api/investimentos/aporte/calcular`.
3. **Validação E2E no Frontend**:
   - Simular inserção de cotações com quedas >20% e verificar o surgimento do card no Sniper Mode.
