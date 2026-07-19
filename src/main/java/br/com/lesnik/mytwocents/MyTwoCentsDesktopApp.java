package br.com.lesnik.mytwocents;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.h2.tools.ChangeFileEncryption;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MyTwoCentsDesktopApp extends Application {

    private static final String DB_DIR = System.getProperty("user.home") + File.separator + ".mytwocents";
    private static final String DB_NAME = "data";
    private static final String DB_FILE = DB_DIR + File.separator + DB_NAME + ".mv.db";
    private static final String ENCRYPTED_MARKER = DB_DIR + File.separator + ".encrypted";

    private static final int DEFAULT_PORT = 8085;
    private static final int MAX_PORT     = 9000;

    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;

    /** Porta efetivamente usada pelo servidor Spring Boot. */
    private int serverPort = DEFAULT_PORT;

    /**
     * IMPORTANTE: Mantemos uma referência forte (campo da classe) para a
     * JavaBridge.
     * Sem isso, o Garbage Collector do Java pode coletar o objeto após a primeira
     * chamada do JS,
     * fazendo com que as chamadas subsequentes do window.javaBridge falhem
     * silenciosamente.
     */
    private JavaBridge javaBridge;

    // ─── JAVA BRIDGE ─────────────────────────────────────────────────────────────

    /**
     * Ponte entre o JavaScript (WebView) e o Java nativo.
     * Permite ao JS solicitar o download de arquivos usando o FileChooser do
     * sistema.
     */
    public class JavaBridge {
        public void saveFile(String urlPath, String suggestedName, String password) {
            Platform.runLater(() -> {
                try {
                    String ext = suggestedName.substring(suggestedName.lastIndexOf('.') + 1).toUpperCase();

                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Salvar Exportação");
                    fileChooser.setInitialFileName(suggestedName);
                    fileChooser.getExtensionFilters().add(
                            new FileChooser.ExtensionFilter("Arquivo " + ext, "*." + ext.toLowerCase()));

                    File file = fileChooser.showSaveDialog(primaryStage);
                    if (file == null)
                        return;

                    URL url = new URL("http://localhost:" + serverPort + urlPath);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    if (password != null) {
                        conn.setRequestProperty("X-Backup-Password", password);
                    }

                    try (InputStream in = conn.getInputStream();
                            FileOutputStream out = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    conn.disconnect();

                    WebView wv = findWebView(primaryStage.getScene().getRoot());
                    if (wv != null)
                        wv.getEngine().executeScript("showToast('Arquivo salvo com sucesso!')");

                } catch (Exception e) {
                    e.printStackTrace();
                    showErrorInJs("Erro ao salvar: " + e.getMessage());
                }
            });
        }

        public void importFile() {
            Platform.runLater(() -> {
                try {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Selecionar Backup para Restaurar");
                    fileChooser.getExtensionFilters().add(
                            new FileChooser.ExtensionFilter("Backup Encriptado (*.mtc)", "*.mtc"));

                    File file = fileChooser.showOpenDialog(primaryStage);
                    if (file == null)
                        return;

                    TextInputDialog pwdDialog = new TextInputDialog();
                    pwdDialog.setTitle("Senha do Backup");
                    pwdDialog.setHeaderText("Este backup está encriptado.");
                    pwdDialog.setContentText("Digite a senha usada na exportação:");
                    pwdDialog.showAndWait();

                    String password = pwdDialog.getResult();
                    if (password == null || password.isBlank())
                        return;

                    Alert confirm = new Alert(
                            Alert.AlertType.WARNING,
                            "ATENÇÃO: A restauração substituirá TODOS os dados atuais.\n\nArquivo: " + file.getName()
                                    + "\n\nDeseja continuar?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Confirmação de Restauração");
                    confirm.setHeaderText(null);
                    confirm.showAndWait();

                    if (confirm.getResult() != ButtonType.YES)
                        return;

                    String boundary = "---" + System.currentTimeMillis();
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://localhost:" + serverPort + "/api/backup/import"))
                            .header("X-Backup-Password", password)
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(createMultipartBody(file, boundary))
                            .build();

                    java.net.http.HttpResponse<String> response = client.send(request,
                            java.net.http.HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        WebView wv = findWebView(primaryStage.getScene().getRoot());
                        if (wv != null)
                            wv.getEngine().executeScript(
                                    "showToast('Backup restaurado! Recarregando...'); setTimeout(() => location.reload(), 1500);");
                    } else {
                        showErrorInJs("Falha na restauração: " + response.body());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    showErrorInJs("Erro durante a importação: " + e.getMessage());
                }
            });
        }

        private java.net.http.HttpRequest.BodyPublisher createMultipartBody(File file, String boundary)
                throws IOException {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] prefix = ("--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n").getBytes();
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes();

            byte[] total = new byte[prefix.length + fileBytes.length + suffix.length];
            System.arraycopy(prefix, 0, total, 0, prefix.length);
            System.arraycopy(fileBytes, 0, total, prefix.length, fileBytes.length);
            System.arraycopy(suffix, 0, total, prefix.length + fileBytes.length, suffix.length);

            return java.net.http.HttpRequest.BodyPublishers.ofByteArray(total);
        }

        private void showErrorInJs(String msg) {
            try {
                WebView wv = findWebView(primaryStage.getScene().getRoot());
                if (wv != null)
                    wv.getEngine().executeScript("showToast('" + msg.replace("'", "") + "', 'error')");
            } catch (Exception ignored) {
            }
        }

        private WebView findWebView(javafx.scene.Parent root) {
            if (root instanceof StackPane) {
                for (javafx.scene.Node node : ((StackPane) root).getChildren()) {
                    if (node instanceof WebView)
                        return (WebView) node;
                }
            }
            return null;
        }
    }

    // ─── LIFECYCLE ───────────────────────────────────────────────────────────────

    @Override
    public void init() {
        // Configura propriedades de compatibilidade (sem iniciar Spring aqui)
        System.setProperty("java.awt.headless", "false");
        System.setProperty("spring.main.headless", "false");
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Configurações básicas do Stage
        primaryStage.setTitle("MyTwoCents - Financeiro");
        primaryStage.setMinWidth(1280);
        primaryStage.setMinHeight(720);
        try {
            InputStream iconStream = getClass().getResourceAsStream("/static/icon.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
        } catch (Exception ignored) {
        }
        primaryStage.setOnCloseRequest(event -> {
            event.consume(); // Cancela o encerramento imediato do JavaFX
            showAutoClosingAlert(Alert.AlertType.INFORMATION,
                    "Encerrando o MyTwoCents",
                    "Todas as conexões ativas e o banco de dados estão sendo encerrados com total segurança.",
                    5,
                    this::shutdownApp);
        });

        // ── Determinar estado do banco de dados ──
        File markerFile = new File(ENCRYPTED_MARKER);
        File dbFile = new File(DB_FILE);
        boolean isEncrypted = markerFile.exists();
        boolean dbExists = dbFile.exists();

        String masterPassword;

        if (isEncrypted) {
            // Banco já criptografado → Loop de Login
            masterPassword = loginLoop();
            if (masterPassword == null)
                return;
        } else if (dbExists) {
            // Banco antigo sem criptografia → Migrar
            masterPassword = showSetupDialog(
                    "Seus dados atuais serão protegidos com esta senha.\n" +
                            "Todos os seus lançamentos serão preservados.");
            if (masterPassword == null) {
                showAutoClosingAlert(Alert.AlertType.INFORMATION,
                        "Configuração Cancelada",
                        "A configuração da senha foi cancelada pelo usuário.",
                        5,
                        this::shutdownApp);
                return;
            }

            try {
                migrateDatabase(masterPassword);
            } catch (Exception e) {
                e.printStackTrace();
                showAutoClosingAlert(Alert.AlertType.ERROR,
                        "Erro na Migração",
                        "Não foi possível encriptar o banco de dados e a aplicação será encerrada.\n\nErro: "
                                + e.getMessage(),
                        5,
                        this::shutdownApp);
                return;
            }
        } else {
            // Instalação nova → Setup
            masterPassword = showSetupDialog(
                    "Você está configurando o MyTwoCents pela primeira vez.");
            if (masterPassword == null) {
                showAutoClosingAlert(Alert.AlertType.INFORMATION,
                        "Configuração Cancelada",
                        "A configuração inicial foi cancelada pelo usuário.",
                        5,
                        this::shutdownApp);
                return;
            }

            try {
                new File(DB_DIR).mkdirs();
                new File(ENCRYPTED_MARKER).createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                showAutoClosingAlert(Alert.AlertType.ERROR,
                        "Erro de Configuração",
                        "Não foi possível criar os arquivos iniciais.\n\nErro: " + e.getMessage(),
                        5,
                        this::shutdownApp);
                return;
            }
        }

        // Configura o datasource encriptado para o Spring
        configureEncryptedDatabase(masterPassword);

        // Mostra tela de carregamento e inicia o Spring Boot em segundo plano
        showLoadingAndStartApp();
    }

    @Override
    public void stop() {
        shutdownApp();
    }

    private void shutdownApp() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
        System.exit(0);
    }

    // ─── DIÁLOGOS DE SENHA ───────────────────────────────────────────────────────

    private enum PasswordValidationResult {
        SUCCESS,
        WRONG_PASSWORD,
        DB_LOCKED
    }

    /**
     * Loop de login: pede a senha e valida. Repete até acertar ou cancelar.
     */
    private String loginLoop() {
        while (true) {
            String password = showLoginDialog();
            if (password == null) {
                showAutoClosingAlert(Alert.AlertType.INFORMATION,
                        "Acesso Cancelado",
                        "O login foi cancelado pelo usuário.",
                        5,
                        this::shutdownApp);
                return null;
            }
            PasswordValidationResult result = validatePassword(password);
            if (result == PasswordValidationResult.SUCCESS) {
                return password;
            } else if (result == PasswordValidationResult.DB_LOCKED) {
                showAutoClosingAlert(Alert.AlertType.ERROR,
                        "Banco de Dados Bloqueado",
                        "O banco de dados está em uso ou bloqueado por outra instância do aplicativo.\n" +
                                "O aplicativo será encerrado de forma segura para evitar corrupção dos dados.\n\n" +
                                "Por favor, feche qualquer outra janela do MyTwoCents e tente novamente.",
                        5,
                        this::shutdownApp);
                return null;
            }
            showErrorAlert("Senha Incorreta",
                    "A senha informada não corresponde ao banco de dados.\nTente novamente.");
        }
    }

    /**
     * Diálogo de Login (execuções subsequentes).
     */
    private String showLoginDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("MyTwoCents - Desbloqueio");
        dialog.setHeaderText(null);

        ButtonType unlockType = new ButtonType("Desbloquear", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(unlockType, ButtonType.CANCEL);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Senha Mestra");
        passwordField.setPrefWidth(300);

        Label titleLabel = new Label("🔑 Digite sua Senha Mestra");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label infoLabel = new Label("Digite a senha para acessar seus dados financeiros.");
        infoLabel.setStyle("-fx-text-fill: #666;");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20, 20, 10, 20));
        content.getChildren().addAll(titleLabel, infoLabel, new Separator(), passwordField);

        dialog.getDialogPane().setContent(content);

        // Desabilita botão se campo vazio
        dialog.getDialogPane().lookupButton(unlockType)
                .disableProperty().bind(passwordField.textProperty().isEmpty());

        Platform.runLater(passwordField::requestFocus);

        dialog.setResultConverter(button -> {
            if (button == unlockType)
                return passwordField.getText();
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    /**
     * Diálogo de Setup (primeira execução ou migração). Pede senha 2x para
     * confirmar.
     */
    private String showSetupDialog(String contextMessage) {
        while (true) {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("MyTwoCents - Configuração Inicial");
            dialog.setHeaderText(null);

            ButtonType createType = new ButtonType("Criar Senha", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

            PasswordField pwd1 = new PasswordField();
            pwd1.setPromptText("Digite a senha");
            pwd1.setPrefWidth(300);

            PasswordField pwd2 = new PasswordField();
            pwd2.setPromptText("Confirme a senha");
            pwd2.setPrefWidth(300);

            Label titleLabel = new Label("🔒 Crie sua Senha Mestra");
            titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            Label contextLabel = new Label(contextMessage);
            contextLabel.setWrapText(true);
            contextLabel.setStyle("-fx-text-fill: #666;");

            Label matchLabel = new Label("");
            matchLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");

            // Feedback em tempo real sobre correspondência
            pwd2.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.isEmpty()) {
                    matchLabel.setText("");
                } else if (!newVal.equals(pwd1.getText())) {
                    matchLabel.setText("✗ As senhas não coincidem");
                    matchLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
                } else {
                    matchLabel.setText("✓ As senhas coincidem");
                    matchLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11px;");
                }
            });

            Label warningLabel = new Label(
                    "⚠ IMPORTANTE: Se você esquecer esta senha, seus dados\n" +
                            "NÃO poderão ser recuperados. Faça backups regulares\n" +
                            "pelo menu Configurações.");
            warningLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 11px;");

            VBox content = new VBox(10);
            content.setPadding(new Insets(20, 20, 10, 20));
            content.getChildren().addAll(
                    titleLabel, contextLabel,
                    new Separator(),
                    new Label("Senha:"), pwd1,
                    new Label("Confirmar Senha:"), pwd2,
                    matchLabel,
                    new Separator(),
                    warningLabel);

            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().setPrefWidth(450);

            // Botão OK só ativo quando senhas coincidem e não estão vazias
            dialog.getDialogPane().lookupButton(createType).disableProperty().bind(
                    Bindings.createBooleanBinding(
                            () -> pwd1.getText().isEmpty() || !pwd1.getText().equals(pwd2.getText()),
                            pwd1.textProperty(), pwd2.textProperty()));

            Platform.runLater(pwd1::requestFocus);

            dialog.setResultConverter(button -> {
                if (button == createType)
                    return pwd1.getText();
                return null;
            });

            String result = dialog.showAndWait().orElse(null);
            if (result == null)
                return null; // Cancelou

            // Validações extras
            if (result.contains(" ")) {
                showErrorAlert("Senha Inválida", "A senha não pode conter espaços.");
                continue;
            }
            if (result.length() < 4) {
                showErrorAlert("Senha Muito Curta", "A senha deve ter pelo menos 4 caracteres.");
                continue;
            }
            return result;
        }
    }

    // ─── BANCO DE DADOS ──────────────────────────────────────────────────────────

    /**
     * Valida a senha contra o banco H2 encriptado existente.
     */
    private PasswordValidationResult validatePassword(String password) {
        String url = "jdbc:h2:file:" + DB_DIR + File.separator + DB_NAME + ";CIPHER=AES;IFEXISTS=TRUE";
        try (Connection conn = DriverManager.getConnection(url, "sa", password + " ")) {
            return PasswordValidationResult.SUCCESS;
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message != null && (message.contains("locked") || message.contains("already in use")
                    || message.contains("bloqueado"))) {
                return PasswordValidationResult.DB_LOCKED;
            }
            return PasswordValidationResult.WRONG_PASSWORD;
        }
    }

    /**
     * Migra o banco de dados existente (sem criptografia) para AES-256.
     * Usa a ferramenta nativa do H2 para encriptar o arquivo in-place.
     */
    private void migrateDatabase(String password) throws SQLException {
        // Garante que o diretório existe
        new File(DB_DIR).mkdirs();

        // Encripta o banco in-place usando a ferramenta nativa do H2
        ChangeFileEncryption.execute(DB_DIR, DB_NAME, "AES", null, password.toCharArray(), true);

        // Cria arquivo marcador indicando que o banco está encriptado
        try {
            new File(ENCRYPTED_MARKER).createNewFile();
        } catch (IOException e) {
            throw new SQLException("Falha ao criar arquivo marcador de encriptação.", e);
        }
    }

    /**
     * Configura as System Properties do Spring para usar o banco encriptado.
     * O Spring usará estas propriedades ao inicializar o DataSource.
     */
    /**
     * Encontra a primeira porta TCP disponível no intervalo [DEFAULT_PORT, MAX_PORT].
     * Tenta abrir um ServerSocket em cada porta; a primeira que abrir sem exceção
     * é considerada livre.
     *
     * @return número da porta disponível
     * @throws IllegalStateException se nenhuma porta estiver disponível no intervalo
     */
    private int findAvailablePort() {
        for (int port = DEFAULT_PORT; port <= MAX_PORT; port++) {
            try (ServerSocket socket = new ServerSocket(port)) {
                socket.setReuseAddress(true);
                return port; // porta livre encontrada
            } catch (IOException ignored) {
                // porta ocupada, tenta a próxima
            }
        }
        throw new IllegalStateException(
                "Nenhuma porta disponível no intervalo " + DEFAULT_PORT + "–" + MAX_PORT + ". " +
                "Libere uma porta e tente novamente.");
    }

    private void configureEncryptedDatabase(String password) {
        // ── Porta dinâmica ──────────────────────────────────────────────────────
        serverPort = findAvailablePort();
        System.setProperty("server.port", String.valueOf(serverPort));

        // ── Datasource ──────────────────────────────────────────────────────────
        String url = "jdbc:h2:file:" + DB_DIR + File.separator + DB_NAME
                + ";CIPHER=AES;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
        System.setProperty("spring.datasource.url", url);
        // Formato H2 para banco encriptado: "senhaDoArquivo senhaDoUsuario"
        // Como a senha do SA é vazia, fica: "senhaMestra " (com espaço no final)
        System.setProperty("spring.datasource.password", password + " ");
    }

    // ─── INICIALIZAÇÃO DO APP ────────────────────────────────────────────────────

    /**
     * Mostra uma tela de carregamento elegante e inicia o Spring Boot em segundo
     * plano.
     * Quando o Spring estiver pronto, carrega o WebView com a interface.
     */
    private void showLoadingAndStartApp() {
        // ── Loading Screen ──
        VBox loadingBox = new VBox(20);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setStyle("-fx-background-color: #0a0e17;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(60, 60);
        spinner.setStyle("-fx-progress-color: #10b981;");

        Label appTitle = new Label("MyTwoCents");
        appTitle.setStyle("-fx-text-fill: #10b981; -fx-font-size: 28px; -fx-font-weight: bold;");

        Label loadingLabel = new Label("Preparando seus dados...");
        loadingLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 14px;");

        loadingBox.getChildren().addAll(appTitle, spinner, loadingLabel);

        Scene loadingScene = new Scene(loadingBox, 1280, 760);
        primaryStage.setScene(loadingScene);
        primaryStage.show();

        // ── Inicia Spring Boot em thread separada ──
        new Thread(() -> {
            try {
                springContext = SpringApplication.run(MyTwoCentsApplication.class);
                Platform.runLater(this::setupWebView);
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showErrorAlert("Erro de Inicialização",
                            "Não foi possível iniciar o servidor interno na porta " + serverPort + ".\n\n" +
                                    "Verifique se não há outra instância do app aberta " +
                                    "ou se todas as portas no intervalo 8085–9000 estão ocupadas.\n\nErro: " + e.getMessage());
                    Platform.exit();
                    System.exit(1);
                });
            }
        }).start();
    }

    /**
     * Configura o WebView, injeta a JavaBridge e substitui a tela de loading.
     */
    private void setupWebView() {
        WebView webView = new WebView();

        // Intercepta confirm() do JS
        webView.getEngine().setConfirmHandler(message -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
            alert.setTitle("MyTwoCents - Confirmação");
            alert.setHeaderText(null);
            alert.showAndWait();
            return alert.getResult() == ButtonType.YES;
        });

        // Intercepta prompt() do JS (necessário para a senha do backup)
        webView.getEngine().setPromptHandler(promptData -> {
            TextInputDialog dialog = new TextInputDialog(promptData.getDefaultValue());
            dialog.setTitle("MyTwoCents - Entrada de Dados");
            dialog.setHeaderText(null);
            dialog.setContentText(promptData.getMessage());
            return dialog.showAndWait().orElse(null);
        });

        // Intercepta cliques em links com target="_blank"
        webView.getEngine().setCreatePopupHandler(config -> {
            WebView tempWebView = new WebView();
            tempWebView.getEngine().locationProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && !newValue.isEmpty() && !newValue.equals("about:blank")) {
                    Platform.runLater(() -> {
                        tempWebView.getEngine().getLoadWorker().cancel();
                        getHostServices().showDocument(newValue);
                    });
                }
            });
            return tempWebView.getEngine();
        });

        // Intercepta navegações diretas para sites externos no próprio WebView
        webView.getEngine().locationProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty() && !newValue.equals("about:blank")) {
                if (!newValue.startsWith("http://localhost:" + serverPort)) {
                    Platform.runLater(() -> {
                        webView.getEngine().getLoadWorker().cancel();
                        getHostServices().showDocument(newValue);
                    });
                }
            }
        });

        // Injeta a JavaBridge quando a página carrega
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == Worker.State.SUCCEEDED) {
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                        javafx.util.Duration.millis(200));
                pause.setOnFinished(e -> {
                    try {
                        javaBridge = new JavaBridge();
                        JSObject window = (JSObject) webView.getEngine().executeScript("window");
                        window.setMember("javaBridge", javaBridge);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                pause.play();
            }
        });

        webView.getEngine().load("http://localhost:" + serverPort);

        // Substitui a loading screen pelo WebView
        StackPane root = new StackPane();
        root.getChildren().add(webView);
        primaryStage.getScene().setRoot(root);
    }

    // ─── UTILITÁRIOS ─────────────────────────────────────────────────────────────

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAutoClosingAlert(Alert.AlertType alertType, String title, String message, int seconds,
            Runnable onFinished) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);

        ButtonType closeButton = new ButtonType("Fechar Imediatamente", ButtonBar.ButtonData.OK_DONE);
        alert.getDialogPane().getButtonTypes().setAll(closeButton);

        final int[] remaining = { seconds };
        alert.setContentText(message + "\n\nFechando automaticamente em " + remaining[0] + " segundos...");
        alert.show();

        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), event -> {
                    remaining[0]--;
                    if (remaining[0] <= 0) {
                        alert.close();
                    } else {
                        alert.setContentText(
                                message + "\n\nFechando automaticamente em " + remaining[0] + " segundos...");
                    }
                }));
        timeline.setCycleCount(seconds);
        timeline.setOnFinished(e -> {
            alert.close();
            if (onFinished != null) {
                onFinished.run();
            }
        });

        alert.setOnHidden(e -> {
            timeline.stop();
            if (onFinished != null) {
                onFinished.run();
            }
        });

        timeline.play();
    }
}
