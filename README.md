# 💰 FControl – Controle Financeiro Desktop

**FControl** é uma aplicação de controle financeiro pessoal poderosa e intuitiva, convertida em um **aplicativo Desktop nativo** para máxima performance e privacidade. Gerencie receitas, despesas e assinaturas com uma interface moderna e relatórios automatizados.

---

## 📸 Visão Geral
O FControl oferece uma experiência de "Aplicativo Standalone" usando JavaFX WebView, integrando uma interface rica em Javascript com o poder e segurança do Java/Spring Boot no backend.

---

## 🚀 Como Rodar e Construir

### Modo Desenvolvimento
Se você deseja modificar o código ou rodar via terminal para testes:

```bash
# Inicie o servidor e a interface desktop
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./mvnw spring-boot:run
```
> O app abrirá automaticamente em uma janela nativa na porta **8085**.

### 📦 Gerar Instalador (Linux/RPM)
Para gerar o instalador nativa para sistemas baseados em Fedora/Nobara:

```bash
chmod +x build-installer.sh
./build-installer.sh
```
O arquivo `.rpm` será gerado na pasta `release/`. O processo utiliza `jlink` para criar um JRE customizado e otimizado, garantindo que o app rode mesmo que o computador não tenha Java instalado.

---

## 🛠️ Tecnologias de Ponta

- **Backend:** Spring Boot 3.3.5 (Java 17)
- **Frontend:** Vanilla CSS (Glassmorphism), ES2022, Chart.js 4.4
- **Interface Desktop:** JavaFX WebView (WebKit)
- **Persistência:** H2 Database (modo arquivo em `~/.fcontrol/data`)
- **Empacotamento:** jpackage + jlink para JRE reduzido e estável

---

## 📖 Principais Funcionalidades

### 📊 Dashboards Dual-View
- **Anual**: Evolução do saldo e gastos ao longo de todo o ano.
- **Mensal**: Detalhamento dinâmico (Donut + Top 5) focado no mês selecionado.

### 💰 Gestão Prática
- **Máscara Monetária**: Entrada de valores formatada em tempo real (R$).
- **Exportação Inteligente**: Relatórios em **PDF** e **CSV** com nomes de arquivo dinâmicos (ex: `FControl - 2026 - Abril - Receitas.pdf`).
- **Ponte Nativa**: Diálogos de salvamento ("Save As") integrados ao sistema operacional.

### 💖 Apoio ao Projeto
- Botão **Pix** integrado ao rodapé para apoio direto ao desenvolvedor.

---

## 🗄️ Onde meus dados ficam salvos?

Para garantir a segurança e facilidade de backup, o FControl salva os dados na pasta pessoal do seu usuário:
- **Caminho:** `/home/USUARIO/.fcontrol/data.mv.db`

---

## ⚙️ Configurações Técnicas
O arquivo `src/main/resources/application.properties` contém as definições de porta e persistência:
- **Porta Padrão:** 8085
- **Headless:** Desativado (necessário para diálogos nativos)

---

## 🛠️ Resolução de Problemas (Troubleshooting)

### O app fecha logo após abrir?
Geralmente ocorre se a porta **8085** estiver ocupada. Verifique se outra instância não está rodando. Esta versão possui um mecanismo de diagnóstico que mostrará um alerta caso ocorra falha na inicialização do servidor.

---

© 2026 Leandro Lesnik
