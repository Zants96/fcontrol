package br.com.lesnik.mytwocents.desktop;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

/**
 * Encapsula a lógica de diálogo de importação e exportação de backups via JavaFX Native FileChooser
 * e faz a ponte HTTP com o servidor local Spring Boot.
 */
public class DesktopBackupHandler {

    private final Stage primaryStage;
    private final int serverPort;

    public DesktopBackupHandler(Stage primaryStage, int serverPort) {
        this.primaryStage = primaryStage;
        this.serverPort = serverPort;
    }

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
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + serverPort + "/api/backup/import"))
                        .header("X-Backup-Password", password)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(createMultipartBody(file, boundary))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

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

    private HttpRequest.BodyPublisher createMultipartBody(File file, String boundary) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        byte[] prefix = ("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n").getBytes();
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes();

        byte[] total = new byte[prefix.length + fileBytes.length + suffix.length];
        System.arraycopy(prefix, 0, total, 0, prefix.length);
        System.arraycopy(fileBytes, 0, total, prefix.length, fileBytes.length);
        System.arraycopy(suffix, 0, total, prefix.length + fileBytes.length, suffix.length);

        return HttpRequest.BodyPublishers.ofByteArray(total);
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
        if (root instanceof javafx.scene.layout.StackPane) {
            for (javafx.scene.Node node : ((javafx.scene.layout.StackPane) root).getChildren()) {
                if (node instanceof WebView)
                    return (WebView) node;
            }
        }
        return null;
    }
}
