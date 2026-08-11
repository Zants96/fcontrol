# 💰 MyTwoCents – Controle Financeiro Pessoal & Investimentos

**MyTwoCents** é uma solução completa, moderna e segura para **Controle Financeiro Pessoal e Gestão de Carteira de Investimentos**, empacotada como uma aplicação **Desktop Nativa (Offline-First)** com aceleração gráfica JavaFX WebView e inteligência artificial acoplada.

---

## 🌟 Principais Recursos

### 📊 1. Gestão de Orçamento & Finanças Pessoais
- **Visão Dual-Dashboard**: Transição imediata entre visão **Anual** (evolução de saldo mês a mês) e **Mensal** (gráficos donut por categoria e top 5 despesas).
- **Lançamentos Parcelados**: Criação de compras parceladas com ajuste dinâmico de grupo.
- **Filtros Avançados**: Busca rápida por categorias (Gastos, Receitas, Investimentos, Transferências) e subcategorias.
- **Exportação Profissional**: Geração instantânea de relatórios em **PDF** e **CSV** com nomes formatados dinamicamente.

### 📈 2. Carteira de Investimentos & Renda Passiva
- **Multi-Ativos**: Suporte nativo para Ações (B3), FIIs (Fundos Imobiliários), Renda Fixa (CDB/LCI/LCA), ETFs, Tesouro Direto e Criptomoedas.
- **Preço Médio e Rendimento**: Cálculo automático de preço médio de compra, resultado acumulado e Yield on Cost.
- **Cotações Automáticas em Tempo Real**: Atualizações transparentes via **BrAPI.dev**, **Banco Central do Brasil (SGS - Selic/IPCA)** e **CoinGecko API**.
- **Histórico de Proventos**: Acompanhamento detalhado de Dividendos, JCP (Juros sobre Capital Próprio) e Rendimentos.

### 🤖 3. Assistente de IA Financeira (Google Gemini)
- **Consultoria Holística**: IA treinada com filosofia sênior de alocação de carteira (Value Investing, Preço-Teto, ROIC) e eficiência de fluxo de caixa doméstico.
- **Parser de Notas/Documentos**: Extração automática de dados financeiros a partir de textos colados de faturas, extratos ou recibos.
- **Insights Automáticos**: Geração de alertas de desperdício, metas e dicas classificadas por nível de impacto (Alto/Médio/Baixo).

### 🔒 4. Privacidade e Segurança Privada (Privacy-First)
- **Encriptação Mestra AES**: Banco de dados H2 criptografado localmente no dispositivo do usuário com senha mestra de desbloqueio.
- **Backup & Restauração Encriptada (`.mtc`)**: Exportação de backups completos criptografados por senha e importação protegida.

---

## 🖥️ Como Executar a Aplicação

### Pré-requisitos
- **Java Development Kit (JDK) 17** ou superior (Recomendado: Eclipse Temurin 17).

### Execução em Modo Desenvolvimento

Para rodar a aplicação diretamente pelo terminal:

```bash
# Definir JAVA_HOME e iniciar o servidor Spring Boot com a interface Desktop
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./mvnw spring-boot:run
```

Ou compilando o pacote JAR executável:

```bash
# 1. Compilar o pacote
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./mvnw clean package

# 2. Executar o JAR
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk java -jar target/mytwocents-0.0.1-SNAPSHOT.jar
```

---

## 📦 Gerar Instaladores Nativos

### Linux (RPM para Fedora / Nobara / RHEL)

Certifique-se de que a variável `JAVA_HOME` está apontando para o JDK 17 e execute:

```bash
chmod +x build-installer.sh
./build-installer.sh
```

O arquivo instalador `.rpm` será gerado no diretório `release/`. O script utiliza `jlink` com compressão e strip de debug para incluir uma JRE customizada ultra-leve.

### Windows (.exe / .msi)

No Windows (requer **WiX Toolset** instalado para pacotes MSI):

```cmd
build-installer-windows.bat
```

O instalador nativo executável será gerado dentro da pasta `release\`.

---

## 🧪 Suíte de Testes & Validação de Saúde

Para executar a suíte completa de testes de integração e serviços (banco H2, criptografia, exportadores e controllers):

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./mvnw test
```

---

## 🗄️ Armazenamento e Localização dos Dados

Todos os seus dados residem **exclusivamente no seu computador**:

- **Linux / macOS**: `/home/USUARIO/.mytwocents/data.mv.db`
- **Windows**: `C:\Users\USUARIO\.mytwocents\data.mv.db`

---

## 🛠️ Arquitetura Tecnológica

- **Backend**: Java 17, Spring Boot 3.3.5, Spring Data JPA, Flyway DB Migration.
- **Banco de Dados**: H2 Database (modo arquivo embarcado com criptografia CIPHER AES).
- **Interface Desktop**: JavaFX 17 WebView (Engine WebKit nativo com ponte JS `window.javaBridge`).
- **Frontend**: Vanilla HTML5, CSS3 (Glassmorphism), JavaScript ES6 (Store Pub/Sub), Chart.js.

---

© 2026 Leandro Lesnik & Contribuidores
