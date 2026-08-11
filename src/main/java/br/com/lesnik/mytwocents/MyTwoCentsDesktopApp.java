package br.com.lesnik.mytwocents;

import br.com.lesnik.mytwocents.desktop.DesktopBackupHandler;
import br.com.lesnik.mytwocents.desktop.DesktopUpdateChecker;
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
    private static final String HINT_FILE = DB_DIR + File.separator + ".hint";

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
        private DesktopBackupHandler backupHandler;

        private DesktopBackupHandler getBackupHandler() {
            if (backupHandler == null) {
                backupHandler = new DesktopBackupHandler(primaryStage, serverPort);
            }
            return backupHandler;
        }

        public void saveFile(String urlPath, String suggestedName, String password) {
            getBackupHandler().saveFile(urlPath, suggestedName, password);
        }

        public void importFile() {
            getBackupHandler().importFile();
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
            if ("__RESET_DB__".equals(password)) {
                // Usuário optou por zerar o banco de dados
                String newPassword = showSetupDialog(
                        "Você resetou a aplicação. Crie uma nova senha mestra para o banco de dados zerado.");
                if (newPassword == null) {
                    showAutoClosingAlert(Alert.AlertType.INFORMATION,
                            "Configuração Cancelada",
                            "A criação da nova senha foi cancelada pelo usuário.",
                            5,
                            this::shutdownApp);
                    return null;
                }
                try {
                    new File(DB_DIR).mkdirs();
                    new File(ENCRYPTED_MARKER).createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return newPassword;
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
        passwordField.setPromptText("Digite sua senha mestra");

        Label titleLabel = new Label("Desbloquear MyTwoCents");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);

        Label infoLabel = new Label("Digite a senha mestra para acessar seus dados financeiros.");
        infoLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");
        infoLabel.setMaxWidth(Double.MAX_VALUE);
        infoLabel.setAlignment(Pos.CENTER);

        // ── Botões de Recuperação (Estilo Padrão do Sistema) ──
        Label recoveryLabel = new Label("Esqueceu a senha?");
        recoveryLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4b5563;");

        String hint = readPasswordHint();
        Button hintBtn = new Button("Dica de Senha");
        hintBtn.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        if (hint != null && !hint.isBlank()) {
            hintBtn.setOnAction(e -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Dica de Senha");
                alert.setHeaderText("Sua Dica de Senha Cadastrada");
                alert.setContentText(hint);
                alert.showAndWait();
            });
        } else {
            hintBtn.setOnAction(e -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Dica de Senha");
                alert.setHeaderText(null);
                alert.setContentText("Nenhuma dica de senha foi cadastrada para esta base de dados.\n\nVocê pode definir uma dica ao criar ou restaurar a senha mestra.");
                alert.showAndWait();
            });
        }

        Button restoreBtn = new Button("Restaurar Backup");
        restoreBtn.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        restoreBtn.setOnAction(e -> {
            String restoredPassword = handleRestoreBackupFromLogin();
            if (restoredPassword != null) {
                dialog.setResult(restoredPassword);
                dialog.close();
            }
        });

        Button resetBtn = new Button("Zerar Banco");
        resetBtn.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        resetBtn.setOnAction(e -> {
            if (handleFactoryReset()) {
                dialog.setResult("__RESET_DB__");
                dialog.close();
            }
        });

        javafx.scene.layout.HBox actionsBox = new javafx.scene.layout.HBox(8);
        actionsBox.setAlignment(Pos.CENTER);
        actionsBox.getChildren().addAll(hintBtn, restoreBtn, resetBtn);

        VBox recoverySection = new VBox(6);
        recoverySection.setAlignment(Pos.CENTER);
        recoverySection.getChildren().addAll(recoveryLabel, actionsBox);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16, 16, 12, 16));
        content.getChildren().addAll(titleLabel, infoLabel, new Separator(), passwordField, new Separator(), recoverySection);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(440);

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

            TextField hintField = new TextField();
            hintField.setPromptText("Ex: Nome do meu primeiro pet");
            hintField.setPrefWidth(300);

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
                            "NÃO poderão ser recuperados sem um backup.\n" +
                            "Faça backups regulares pelo menu Configurações.");
            warningLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 11px;");

            VBox content = new VBox(10);
            content.setPadding(new Insets(20, 20, 10, 20));
            content.getChildren().addAll(
                    titleLabel, contextLabel,
                    new Separator(),
                    new Label("Senha:"), pwd1,
                    new Label("Confirmar Senha:"), pwd2,
                    matchLabel,
                    new Label("Dica de Senha (Opcional):"), hintField,
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
            savePasswordHint(hintField.getText());
            return result;
        }
    }

    // ─── MÉTODOS DE RECUPERAÇÃO E GERENCIAMENTO DE ACESSO ────────────────────────

    private void savePasswordHint(String hint) {
        File file = new File(HINT_FILE);
        if (hint == null || hint.isBlank()) {
            if (file.exists()) {
                file.delete();
            }
            return;
        }
        try {
            new File(DB_DIR).mkdirs();
            Files.writeString(file.toPath(), hint.trim(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String readPasswordHint() {
        File file = new File(HINT_FILE);
        if (!file.exists()) {
            return null;
        }
        try {
            String hint = Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            return hint.isBlank() ? null : hint.trim();
        } catch (IOException e) {
            return null;
        }
    }

    private boolean handleFactoryReset() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Zerar Banco de Dados");
        alert.setHeaderText("⚠️ ATENÇÃO: Esta ação é irreversível!");
        alert.setContentText("Todos os seus lançamentos, investimentos e configurações serão apagados permanentemente.\n\n" +
                "Esta opção deve ser usada apenas se você tiver certeza ou desejar começar do zero.\n\n" +
                "Deseja realmente apagar todos os dados?");

        ButtonType confirmBtn = new ButtonType("Sim, Apagar Tudo", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirmBtn, cancelBtn);

        java.util.Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == confirmBtn) {
            deleteDatabaseFiles();
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Banco de Dados Zerado");
            info.setHeaderText(null);
            info.setContentText("Os dados anteriores foram apagados com sucesso.\nAgora você poderá criar uma nova senha mestra.");
            info.showAndWait();
            return true;
        }
        return false;
    }

    private void deleteDatabaseFiles() {
        File dbFile = new File(DB_FILE);
        if (dbFile.exists()) dbFile.delete();

        File traceFile = new File(DB_DIR + File.separator + DB_NAME + ".trace.db");
        if (traceFile.exists()) traceFile.delete();

        File lockFile = new File(DB_DIR + File.separator + DB_NAME + ".lock.db");
        if (lockFile.exists()) lockFile.delete();

        File markerFile = new File(ENCRYPTED_MARKER);
        if (markerFile.exists()) markerFile.delete();

        File hintFile = new File(HINT_FILE);
        if (hintFile.exists()) hintFile.delete();
    }

    private String handleRestoreBackupFromLogin() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Selecionar Backup Encriptado");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Backup Encriptado (*.mtc)", "*.mtc"));

            File file = fileChooser.showOpenDialog(primaryStage);
            if (file == null) return null;

            TextInputDialog pwdDialog = new TextInputDialog();
            pwdDialog.setTitle("Senha do Backup");
            pwdDialog.setHeaderText("Este backup está encriptado.");
            pwdDialog.setContentText("Digite a senha usada na exportação do backup:");
            java.util.Optional<String> pwdResult = pwdDialog.showAndWait();

            if (pwdResult.isEmpty() || pwdResult.get().isBlank()) {
                return null;
            }
            String backupPassword = pwdResult.get();

            byte[] encryptedBytes = Files.readAllBytes(file.toPath());
            byte[] sqlBytes;
            try {
                sqlBytes = decryptBackupData(encryptedBytes, backupPassword);
            } catch (Exception e) {
                showErrorAlert("Erro ao Desencriptar",
                        "A senha informada para o backup está incorreta ou o arquivo está danificado.");
                return null;
            }

            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "ATENÇÃO: A restauração substituirá TODOS os dados atuais pelo conteúdo do backup.\n\nArquivo: " + file.getName()
                            + "\n\nDeseja continuar?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Confirmação de Restauração");
            confirm.setHeaderText(null);
            java.util.Optional<ButtonType> confirmResult = confirm.showAndWait();
            if (confirmResult.isEmpty() || confirmResult.get() != ButtonType.YES) {
                return null;
            }

            // Exclui banco antigo
            deleteDatabaseFiles();
            new File(DB_DIR).mkdirs();

            // Grava o SQL desencriptado em um arquivo temporário para inicializar o H2
            File tempSqlFile = File.createTempFile("login_restore", ".sql");
            Files.write(tempSqlFile.toPath(), sqlBytes);

            String initScript = tempSqlFile.getAbsolutePath().replace("\\", "/");
            String url = "jdbc:h2:file:" + DB_DIR + File.separator + DB_NAME
                    + ";CIPHER=AES;INIT=RUNSCRIPT FROM '" + initScript + "'";

            try (Connection conn = DriverManager.getConnection(url, "sa", backupPassword + " ")) {
                // Banco criado e restaurado com sucesso!
            } finally {
                tempSqlFile.delete();
            }

            new File(ENCRYPTED_MARKER).createNewFile();

            // Pergunta se deseja definir uma dica de senha para esta senha restaurada
            TextInputDialog hintDialog = new TextInputDialog();
            hintDialog.setTitle("Dica de Senha");
            hintDialog.setHeaderText("Backup Restaurado com Sucesso!");
            hintDialog.setContentText("Deseja cadastrar uma dica de senha para lembrar essa senha no futuro? (Opcional):");
            java.util.Optional<String> newHint = hintDialog.showAndWait();
            newHint.ifPresent(this::savePasswordHint);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Restauração Concluída");
            alert.setHeaderText(null);
            alert.setContentText("O backup foi restaurado com sucesso!\nO aplicativo será aberto com seus dados.");
            alert.showAndWait();

            return backupPassword;

        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert("Erro na Restauração", "Não foi possível restaurar o backup.\nErro: " + e.getMessage());
            return null;
        }
    }

    private byte[] decryptBackupData(byte[] encryptedData, String password) throws Exception {
        byte[] key = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        key = sha.digest(key);
        javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(key, "AES");
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(encryptedData);
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
