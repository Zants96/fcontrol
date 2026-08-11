package br.com.lesnik.mytwocents.desktop;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Utilitário Desktop para verificação assíncrona de atualizações via REST API local do Spring Boot.
 */
public class DesktopUpdateChecker {

    private final int serverPort;

    public DesktopUpdateChecker(int serverPort) {
        this.serverPort = serverPort;
    }

    public void checkForUpdatesAsync(boolean notifyIfLatest) {
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + serverPort + "/api/update/check"))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String body = response.body();
                    boolean updateAvailable = body.contains("\"updateAvailable\":true");
                    
                    if (updateAvailable) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                                    "Uma nova atualização do MyTwoCents está disponível!\n\nAcesse as configurações no aplicativo para atualizar.",
                                    ButtonType.OK);
                            alert.setTitle("Atualização Disponível");
                            alert.setHeaderText("Nova versão encontrada");
                            alert.show();
                        });
                    } else if (notifyIfLatest) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                                    "Você já está utilizando a versão mais recente do MyTwoCents.",
                                    ButtonType.OK);
                            alert.setTitle("MyTwoCents - Atualizações");
                            alert.setHeaderText("Aplicação Atualizada");
                            alert.show();
                        });
                    }
                }
            } catch (Exception e) {
                // Silencioso se for verificação automática em background
            }
        }).start();
    }
}
