# FControl – Documentação Técnica

Este documento explica cada arquivo do projeto em detalhe: o que faz, por que existe e como funciona.

---

## 📁 Backend — Camadas da Aplicação

O backend segue a arquitetura em camadas clássica do Spring Boot:

```
Controller  →  Service  →  Repository  →  Database
   (API)      (regras)      (acesso)       (H2)
```

---

### `FcontrolApplication.java`

```
src/main/java/br/com/lesnik/fcontrol/FcontrolApplication.java
```

**O que é:** Ponto de entrada da aplicação Spring Boot.

**Como funciona:**
- A anotação `@SpringBootApplication` ativa o auto-configure do Spring, o scan de componentes e a configuração via anotações.
- O método `main` chama `SpringApplication.run(...)` que inicializa o servidor Tomcat embutido, carrega todos os beans (componentes), conecta ao banco e sobe a aplicação.

Você não precisa modificar este arquivo normalmente.

---

### `model/Categoria.java`

```
src/main/java/br/com/lesnik/fcontrol/model/Categoria.java
```

**O que é:** Um `enum` (enumeração) que define os três tipos possíveis de lançamento financeiro.

**Valores:**

| Valor | Representa |
|-------|-----------|
| `RECEITA` | Entradas de dinheiro (salário, bônus, etc.) |
| `GASTO` | Despesas fixas e variáveis (moradia, alimentação, etc.) |
| `ASSINATURA` | Serviços recorrentes (Netflix, Spotify, etc.) |

**Por que existe:** Garante que nenhum lançamento seja criado com uma categoria inválida. O campo `categoria` na entidade `Lancamento` só aceita esses três valores.

---

### `model/Lancamento.java`

```
src/main/java/br/com/lesnik/fcontrol/model/Lancamento.java
```

**O que é:** A entidade principal representando cada lançamento financeiro no banco de dados.

**Campos:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | `Long` | Identificador único gerado automaticamente |
| `descricao` | `String` | Descrição do lançamento (ex: "Conta de luz de abril") |
| `categoria` | `Categoria` | RECEITA, GASTO ou ASSINATURA |
| `subcategoria` | `String` | Nome do tipo (ex: "Luz", "Netflix", "Salário") |
| `valor` | `BigDecimal` | Valor monetário com precisão de centavos |
| `mes` | `Integer` | Mês do lançamento (1 = Janeiro, 12 = Dezembro) |
| `ano` | `Integer` | Ano do lançamento (ex: 2026) |
| `criadoEm` | `LocalDateTime` | Data/hora de criação (preenchida automaticamente) |

**Anotações importantes:**
- `@Entity` — diz ao JPA que esta classe é uma tabela no banco
- `@Table(name = "lancamento")` — define o nome da tabela como `lancamento`
- `@GeneratedValue(strategy = GenerationType.IDENTITY)` — o banco gera o ID automaticamente (auto-increment)
- `@Enumerated(EnumType.STRING)` — armazena a categoria como texto ("RECEITA") em vez de número (0, 1, 2)
- `@PrePersist` — o método `prePersist()` é chamado automaticamente antes de salvar, preenchendo `criadoEm`

**Lombok simplifica:**
- `@Getter` / `@Setter` — gera os métodos get/set automaticamente
- `@NoArgsConstructor` / `@AllArgsConstructor` — gera construtores
- `@Builder` — permite criar objetos com sintaxe fluente: `Lancamento.builder().descricao("...").valor(...).build()`

---

### `repository/LancamentoRepository.java`

```
src/main/java/br/com/lesnik/fcontrol/repository/LancamentoRepository.java
```

**O que é:** Interface responsável pelo acesso ao banco de dados. Estende `JpaRepository`, o que significa que já herda dezenas de operações prontas (save, findById, findAll, delete, etc.).

**Métodos customizados:**

| Método | O que faz |
|--------|-----------|
| `findByAnoOrderByMesAscCategoriaAscSubcategoriaAsc` | Lista todos os lançamentos de um ano, ordenados |
| `findByAnoAndMesOrderBy...` | Lista lançamentos de um mês específico |
| `findByAnoAndCategoriaOrderBy...` | Lista lançamentos de uma categoria (ex: só GASTOS) |
| `findByAnoAndMesAndCategoriaOrderBy...` | Lista lançamentos de mês + categoria específicos |
| `sumByAnoAndMesAndCategoria` | Soma todos os valores de um mês/categoria (usa `@Query`) |
| `sumByAnoAndCategoria` | Soma todos os valores de um ano inteiro por categoria |
| `findDistinctSubcategoriasByCategoria` | Lista nomes de subcategorias únicas |
| `existsByAnoAndCategoriaAndSubcategoria` | Verifica se já existe um lançamento com esses critérios |

**Como a mágica funciona:** O Spring Data JPA interpreta o nome dos métodos para gerar as queries SQL automaticamente. Ex: `findByAnoAndMes` vira `SELECT * FROM lancamento WHERE ano = ? AND mes = ?`.

---

### `dto/LancamentoDTO.java`

```
src/main/java/br/com/lesnik/fcontrol/dto/LancamentoDTO.java
```

**O que é:** DTO (Data Transfer Object) — um objeto simples para trafegar dados entre o frontend e o backend através da API.

**Por que não usar a entidade diretamente?**  
A entidade `Lancamento` tem campos internos (como `criadoEm`) que não precisam ser expostos pela API. O DTO é uma representação limpa e controlada dos dados.

**Fluxo:**
```
Frontend  →  [JSON]  →  LancamentoDTO  →  LancamentoService  →  Lancamento (entidade)  →  Banco
Banco  →  Lancamento (entidade)  →  LancamentoService  →  LancamentoDTO  →  [JSON]  →  Frontend
```

---

### `dto/DashboardDTO.java`

```
src/main/java/br/com/lesnik/fcontrol/dto/DashboardDTO.java
```

**O que é:** DTO específico para o endpoint do dashboard, carregando todos os dados agregados necessários para os gráficos e cards.

**Campos principais:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `totalReceitas` | `BigDecimal` | Soma anual de receitas |
| `totalGastos` | `BigDecimal` | Soma anual de gastos |
| `totalAssinaturas` | `BigDecimal` | Soma anual de assinaturas |
| `saldoAnual` | `BigDecimal` | Receitas - Gastos - Assinaturas |
| `receitasPorMes` | `List<BigDecimal>` | 12 valores, um por mês |
| `gastosPorMes` | `List<BigDecimal>` | 12 valores, um por mês |
| `assinaturasPorMes` | `List<BigDecimal>` | 12 valores, um por mês |
| `saldoPorMes` | `List<BigDecimal>` | 12 valores, um por mês |
| `gastosPorSubcategoria` | `Map<String, BigDecimal>` | Ex: `{"Moradia": 1200.00, "Luz": 80.00}` |
| `topGastos` | `List<SubcategoriaValor>` | Top 5 maiores gastos do ano |

---

### `service/LancamentoService.java`

```
src/main/java/br/com/lesnik/fcontrol/service/LancamentoService.java
```

**O que é:** A camada de negócio — onde ficam as regras e cálculos da aplicação. O Controller chama o Service, e o Service chama o Repository.

**Métodos:**

#### CRUD básico
- `listarPorAno(ano)` — retorna todos os lançamentos do ano como lista de DTOs
- `listarPorAnoEMes(ano, mes)` — filtra por mês
- `listarPorAnoECategoria(ano, categoria)` — filtra por categoria
- `criar(dto)` — converte o DTO para entidade, salva e retorna o DTO com ID gerado
- `atualizar(id, dto)` — busca o lançamento existente, atualiza os campos e salva
- `excluir(id)` — verifica se existe e deleta

#### Cálculos do Dashboard
`calcularDashboard(ano)` executa:
1. Para cada mês (1 a 12): busca as somas de receitas, gastos e assinaturas → calcula saldo mensal
2. Busca totais anuais de cada categoria
3. Agrupa gastos + assinaturas por subcategoria (para o gráfico donut)
4. Ordena as subcategorias por valor decrescente e pega as 5 maiores (Top 5)
5. Monta e retorna o `DashboardDTO`

#### Mapeadores internos
- `toDTO(Lancamento)` — converte entidade → DTO
- `toEntity(LancamentoDTO)` — converte DTO → entidade

**Anotações:**
- `@Service` — registra como componente Spring
- `@Transactional` — envolve cada operação em uma transação de banco
- `@Transactional(readOnly = true)` — otimização para operações de leitura (não fazem write locks)

---

### `controller/LancamentoController.java`

```
src/main/java/br/com/lesnik/fcontrol/controller/LancamentoController.java
```

**O que é:** A camada de API REST — recebe requisições HTTP, delega ao Service e retorna respostas JSON.

**Endpoints:**

| Método HTTP | Caminho | Parâmetros | Ação |
|-------------|---------|------------|------|
| `GET` | `/api/lancamentos` | `ano`, `mes?`, `categoria?` | Lista lançamentos |
| `POST` | `/api/lancamentos` | Body JSON | Cria lançamento |
| `PUT` | `/api/lancamentos/{id}` | Body JSON | Atualiza lançamento |
| `DELETE` | `/api/lancamentos/{id}` | — | Remove lançamento |
| `GET` | `/api/dashboard` | `ano` | Dados do dashboard |

**Anotações:**
- `@RestController` — combina `@Controller` + `@ResponseBody` (retorna JSON automaticamente)
- `@RequestMapping("/api")` — prefixo base para todos os endpoints
- `@GetMapping`, `@PostMapping`, etc. — mapeiam os métodos HTTP
- `@RequestParam` — parâmetros da URL (`?ano=2026`)
- `@PathVariable` — segmentos da URL (`/lancamentos/{id}`)
- `@RequestBody` — deserializa o JSON do body da requisição para um DTO

---

### `config/CorsConfig.java`

```
src/main/java/br/com/lesnik/fcontrol/config/CorsConfig.java
```

**O que é:** Configuração de CORS (Cross-Origin Resource Sharing).

**Por que existe:** Os navegadores bloqueiam por segurança chamadas HTTP feitas de uma origem diferente. Como o frontend é servido pelo próprio backend (mesma origem), o CORS não é estritamente necessário aqui — mas a config existe para permitir chamadas de ferramentas externas (ex: Postman, curl, ou um frontend rodando em porta diferente durante desenvolvimento).

**O que faz:** Permite que qualquer origem (`*`) acesse os endpoints `/api/**` usando os métodos GET, POST, PUT, DELETE e OPTIONS.

---

### `config/DataInitializer.java`

```
src/main/java/br/com/lesnik/fcontrol/config/DataInitializer.java
```

**O que é:** Componente que roda automaticamente ao iniciar a aplicação e popula o banco com dados padrão.

**Quando roda:** Implementa a interface `CommandLineRunner`, que o Spring executa após toda a inicialização estar completa.

**Lógica:**
1. Verifica se o banco já tem dados: `repository.count() > 0`
2. Se já tem dados → pula (evita duplicar em reinicializações)
3. Se está vazio → cria 34 subcategorias × 12 meses = **408 registros** com valor R$ 0,00 para o ano corrente

**Por que isso é útil:** O usuário já encontra todas as linhas da planilha preenchidas com zero, prontas para editar. Não precisa criar cada subcategoria manualmente do zero.

---

## 📁 Configuração

### `application.properties`

```
src/main/resources/application.properties
```

**O que é:** Arquivo central de configuração do Spring Boot.

**Linha a linha:**

```properties
# Nome da aplicação (aparece nos logs)
spring.application.name=fcontrol

# Caminho do arquivo H2 (./fcontrol-db.mv.db na pasta do projeto)
# AUTO_SERVER=TRUE permite múltiplas conexões simultâneas (ex: console H2 + app)
spring.datasource.url=jdbc:h2:file:./fcontrol-db;AUTO_SERVER=TRUE

spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# update = atualiza o schema sem apagar dados (seguro para uso contínuo)
# create-drop = recria do zero a cada inicialização (apaga tudo!)
spring.jpa.hibernate.ddl-auto=update

# Exibir SQL gerado pelo Hibernate nos logs (útil para debug)
spring.jpa.show-sql=false

# Dialeto SQL do H2
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

# Habilita a interface web do H2 em /h2-console
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# Porta do servidor web
server.port=8080
```

---

## 📁 Frontend — Interface da Aplicação

O frontend é uma **SPA (Single Page Application)** — um único arquivo HTML que muda dinamicamente sem recarregar a página.

---

### `static/index.html`

```
src/main/resources/static/index.html
```

**O que é:** O único arquivo HTML da aplicação. Define toda a estrutura visual.

**Estrutura macro:**

```html
<body>
  <aside class="sidebar">        <!-- Barra lateral de navegação -->
  <main class="main-content">
    <header class="topbar">      <!-- Barra superior com título e data -->
    <section id="view-dashboard"><!-- View do Dashboard (cards + gráficos) -->
    <section id="view-tabela">   <!-- View das Tabelas (Receitas/Gastos/Assinaturas) -->
  </main>
  <div class="modal-overlay">   <!-- Modal de adicionar/editar lançamento -->
  <div class="toast">           <!-- Notificação de feedback (toast) -->
</body>
```

**Como as views funcionam:**  
Apenas uma das `<section>` fica visível por vez. O JavaScript adiciona/remove a classe `hidden` para simular navegação entre "páginas" sem recarregar o HTML.

**Chart.js** é carregado via CDN (sem instalação):
```html
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/..."></script>
```

---

### `static/css/style.css`

```
src/main/resources/static/css/style.css
```

**O que é:** Todo o estilo visual da aplicação. Usa CSS puro (sem Bootstrap, Tailwind ou outros frameworks).

**Organização:**

| Seção | O que define |
|-------|-------------|
| `:root` | Variáveis CSS (cores, espaçamentos, sombras) |
| Reset & Base | Normaliza estilos entre browsers |
| Sidebar | Barra lateral com logo e navegação |
| Topbar | Barra superior |
| Cards | Cards do dashboard com gradientes coloridos |
| Charts | Containers dos gráficos |
| Table | Estilos da tabela de lançamentos |
| Modal | Janela modal com backdrop blur |
| Toast | Notificação flutuante |
| Animations | `@keyframes` para animações |
| Responsive | Media queries para telas menores |

**Variáveis CSS principais:**
```css
:root {
  --bg-base:      #0c0f1a;  /* fundo mais escuro */
  --bg-surface:   #111827;  /* fundo dos cards */
  --bg-elevated:  #1a2235;  /* fundo dos campos de input */
  --brand-primary: #10b981; /* verde esmeralda principal */
  --brand-glow:    #34d399; /* verde mais claro para brilhos */
  --text-primary:  #f1f5f9; /* texto principal */
  --text-secondary:#94a3b8; /* texto secundário/labels */
}
```

Para mudar o tema de cores, basta alterar essas variáveis no `:root`.

---

### `static/js/api.js`

```
src/main/resources/static/js/api.js
```

**O que é:** Módulo JavaScript responsável por toda comunicação HTTP com o backend.

**Por que existe separado:** Centraliza todas as chamadas `fetch()` em um só lugar. Se a URL base mudar ou precisar adicionar autenticação, só precisa alterar aqui.

**Objeto `Api` — métodos:**

| Método | HTTP | Endpoint |
|--------|------|----------|
| `getLancamentos({ano, mes, categoria})` | GET | `/api/lancamentos` |
| `criarLancamento(dto)` | POST | `/api/lancamentos` |
| `atualizarLancamento(id, dto)` | PUT | `/api/lancamentos/{id}` |
| `excluirLancamento(id)` | DELETE | `/api/lancamentos/{id}` |
| `getDashboard(ano)` | GET | `/api/dashboard` |

**Tratamento de erros:** Cada método verifica `res.ok`. Se o servidor retornar um erro HTTP (4xx, 5xx), lança uma exceção que é capturada no `app.js` e exibida como toast de erro.

---

### `static/js/charts.js`

```
src/main/resources/static/js/charts.js
```

**O que é:** Módulo responsável por criar e atualizar os gráficos Chart.js.

**Gráficos implementados:**

#### `renderBarChart(data)`
- Tipo: **barras agrupadas** (`bar`)
- Eixo X: 12 meses
- Dataset 1 (verde): `receitasPorMes`
- Dataset 2 (vermelho): soma de `gastosPorMes` + `assinaturasPorMes`

#### `renderDonutChart(data)`
- Tipo: **rosca** (`doughnut`)
- Dados: `gastosPorSubcategoria` — mostra a distribuição percentual de cada gasto
- Exibe até 10 maiores subcategorias
- Se não houver dados, exibe um estado vazio com ícone

#### `renderLineChart(data)`
- Tipo: **linha** (`line`)
- Eixo X: 12 meses
- Dados: `saldoPorMes`
- Pontos em verde se saldo positivo, vermelho se negativo
- Área abaixo da linha preenchida com gradiente

#### `renderTopGastos(topGastos)`
- Não é Chart.js — é HTML puro
- Renderiza barras de progresso com CSS para os Top 5 gastos

**Destruição de instâncias:** Antes de renderizar um novo gráfico, o existente é destruído com `.destroy()` para evitar memory leaks e sobreposição de canvas.

---

### `static/js/app.js`

```
src/main/resources/static/js/app.js
```

**O que é:** O cérebro da interface. Gerencia o estado, a navegação entre views e toda a interação do usuário.

**Estado global:** Um objeto `state` centraliza a informação atual da interface:

```javascript
const state = {
  view: 'dashboard',    // qual tela está visível
  ano: 2026,            // ano selecionado
  mes: 4,               // mês ativo na tabela
  categoria: null,      // categoria da tabela atual
  lancamentos: [],      // dados carregados na tabela
  editingId: null,      // ID do item sendo editado (null = criação)
};
```

**Fluxo de navegação:**

```
Clique no nav   →   navigateTo(view)
                        ↓
              Atualiza estado e UI
                        ↓
         view='dashboard'  →  loadDashboard()
                                    ↓
                           Api.getDashboard(ano)
                                    ↓
                    renderDashboardCards() + renderBarChart()
                    + renderDonutChart() + renderLineChart()
                    + renderTopGastos()

         view='receitas'   →  buildMonthTabs() + loadTabela()
                                    ↓
                  Api.getLancamentos({ano, mes, categoria})
                                    ↓
                              renderTabela(lancamentos)
```

**Fluxo do Modal:**

```
Clique "+ Adicionar"   →   openCreateModal()   →   showModal()
Clique "✏️ Editar"    →   openEditModal(id)   →   showModal()

Submit do form         →   onFormSubmit()
                                ↓
                    Api.criarLancamento(dto)
                         ou
                    Api.atualizarLancamento(id, dto)
                                ↓
                          closeModal()
                          loadTabela() / loadDashboard()
                          showToast('Lançamento salvo!')
```

**Sistema de toast:**  
Mensagens flutuantes no canto inferior direito, que desaparecem automaticamente após 3,5 segundos. Verde ✅ para sucesso, vermelho ❌ para erros.

---

## 🔄 Fluxo Completo de uma Requisição

Exemplo: usuário edita o valor do "Netflix" de R$ 0,00 para R$ 55,90.

```
1. [BROWSER] Usuário clica em ✏️ na linha "Netflix"
2. [app.js]  openEditModal(42) carrega os dados do state.lancamentos
3. [BROWSER] Modal abre com campos preenchidos
4. [BROWSER] Usuário altera valor para 55.90 e clica "Atualizar"
5. [app.js]  onFormSubmit() coleta os valores do formulário
6. [api.js]  atualizarLancamento(42, {valor: 55.90, ...})
7. [HTTP]    PUT /api/lancamentos/42  { "valor": 55.90, ... }
8. [Controller] LancamentoController.atualizar(42, dto)
9. [Service]    LancamentoService.atualizar(42, dto)
               → busca Lancamento id=42 no banco
               → atualiza o campo valor
               → salva via repository.save()
               → converte para DTO e retorna
10. [HTTP]   200 OK  { "id": 42, "valor": 55.90, ... }
11. [app.js] closeModal() → loadTabela() → renderTabela()
12. [BROWSER] Tabela atualizada com o novo valor
13. [app.js] showToast('Lançamento atualizado! ✅')
```

---

## 🧪 Como Inspecionar o Banco de Dados

Com o servidor rodando, acesse http://localhost:8080/h2-console

Queries úteis:

```sql
-- Ver todos os lançamentos do mês atual
SELECT * FROM LANCAMENTO WHERE ANO = 2026 AND MES = 4 ORDER BY CATEGORIA, SUBCATEGORIA;

-- Ver total de receitas por mês
SELECT MES, SUM(VALOR) AS TOTAL FROM LANCAMENTO
WHERE ANO = 2026 AND CATEGORIA = 'RECEITA'
GROUP BY MES ORDER BY MES;

-- Ver maiores gastos do ano
SELECT SUBCATEGORIA, SUM(VALOR) AS TOTAL FROM LANCAMENTO
WHERE ANO = 2026 AND CATEGORIA IN ('GASTO', 'ASSINATURA')
GROUP BY SUBCATEGORIA ORDER BY TOTAL DESC LIMIT 10;

-- Contar registros por categoria
SELECT CATEGORIA, COUNT(*) AS QTD FROM LANCAMENTO GROUP BY CATEGORIA;
```
