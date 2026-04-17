package br.com.lesnik.fcontrol;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class FControlDesktopApp extends Application {

    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;

    /**
     * IMPORTANTE: Mantemos uma referência forte (campo da classe) para a JavaBridge.
     * Sem isso, o Garbage Collector do Java pode coletar o objeto após a primeira chamada do JS,
     * fazendo com que as chamadas subsequentes do window.javaBridge falhem silenciosamente.
     */
    private JavaBridge javaBridge; 


    /**
     * Ponte entre o JavaScript (WebView) e o Java nativo.
     * Permite ao JS solicitar o download de arquivos usando o FileChooser do sistema.
     */
    public class JavaBridge {
        public void saveFile(String urlPath, String suggestedName) {
            Platform.runLater(() -> {
                try {
                    // Determina a extensão para o filtro do FileChooser
                    String ext = suggestedName.substring(suggestedName.lastIndexOf('.') + 1).toUpperCase();
                    
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Salvar Exportação");
                    fileChooser.setInitialFileName(suggestedName);
                    fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Arquivo " + ext, "*." + ext.toLowerCase())
                    );

                    File file = fileChooser.showSaveDialog(primaryStage);
                    if (file == null) return; // Usuário cancelou

                    // Busca os bytes do servidor local
                    URL url = new URL("http://localhost:8085" + urlPath);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");

                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    conn.disconnect();

                    // Notifica o JS que o download foi concluído
                    primaryStage.getScene().lookup("WebView");
                    WebView wv = (WebView) primaryStage.getScene().getRoot().getChildrenUnmodifiable().get(0);
                    wv.getEngine().executeScript("showToast('Arquivo salvo com sucesso!')");

                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        WebView wv = (WebView) primaryStage.getScene().getRoot().getChildrenUnmodifiable().get(0);
                        wv.getEngine().executeScript("showToast('Erro ao salvar: " + e.getMessage().replace("'", "") + "', 'error')");
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    @Override
    public void init() throws Exception {
        try {
            // Garante que o Spring saiba que não estamos em modo headless (necessário para diálogos e integração desktop)
            // Problemas de AWT/Headless podem causar SIGSEGV em alguns sistemas Linux se não configurados.
            System.setProperty("java.awt.headless", "false");
            System.setProperty("spring.main.headless", "false");

            // Inicia o contexto do Spring Boot
            springContext = SpringApplication.run(FcontrolApplication.class, getParameters().getRaw().toArray(new String[0]));
        } catch (Throwable e) {
            // Captura Throwable para garantir que erros graves (como de JRE/jlink) sejam reportados na UI
            e.printStackTrace();
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Erro de Inicialização");
                alert.setHeaderText("Não foi possível iniciar o servidor interno.");
                alert.setContentText("Verifique se não há outra instância do app aberta ou se a porta 8085 está disponível.\n\nErro: " + e.getMessage());
                alert.showAndWait();
                Platform.exit();
                System.exit(1);
            });
            throw e; 
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        // Criação do Motor Web nativo WebKit
        WebView webView = new WebView();
        
        // Intercepta e renderiza de forma nativa diálogos do Javascript como o confirm() da exclusão
        webView.getEngine().setConfirmHandler(message -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION, message, javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
            alert.setTitle("FControl - Confirmação");
            alert.setHeaderText(null);
            alert.showAndWait();
            return alert.getResult() == javafx.scene.control.ButtonType.YES;
        });

        // Carrega o site local e injeta a Bridge
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                javaBridge = new JavaBridge();
                JSObject window = (JSObject) webView.getEngine().executeScript("window");
                window.setMember("javaBridge", javaBridge);
            }
        });
        
        webView.getEngine().load("http://localhost:8085");

        StackPane root = new StackPane();
        root.getChildren().add(webView);

        Scene scene = new Scene(root, 1280, 720);

        primaryStage.setTitle("FControl - Financeiro");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1280);
        primaryStage.setMinHeight(720);
        
        try {
            java.io.InputStream iconStream = getClass().getResourceAsStream("/static/icon.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
        } catch (Exception e) {
            // Ícone não encontrado, segue o fluxo
        }
        
        // Garante que o usuário pode maximizar mas o app fecha a rede qdo é fechado
        primaryStage.setOnCloseRequest(event -> {
            shutdownApp();
        });
        
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        shutdownApp();
    }
    
    private void shutdownApp() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
        System.exit(0);
    }
}

