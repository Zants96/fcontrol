package br.com.lesnik.mytwocents.controller;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/update")
public class UpdateController {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Zants96/fcontrol/releases/latest";

    // Estado do download atual
    private boolean downloading = false;
    private long totalBytes = 0;
    private long downloadedBytes = 0;
    private String downloadError = null;
    private File downloadedFile = null;

    @Getter
    @Setter
    public static class UpdateInfo {
        private boolean updateAvailable;
        private String currentVersion;
        private String latestVersion;
        private String releaseNotes;
        private String downloadUrl;
        private String fileName;
    }

    @Getter
    public static class DownloadStatus {
        private final boolean downloading;
        private final long totalBytes;
        private final long downloadedBytes;
        private final int progressPercent;
        private final String error;
        private final boolean complete;
        private final String filePath;

        public DownloadStatus(boolean downloading, long totalBytes, long downloadedBytes, String error, boolean complete, String filePath) {
            this.downloading = downloading;
            this.totalBytes = totalBytes;
            this.downloadedBytes = downloadedBytes;
            this.progressPercent = totalBytes > 0 ? (int) ((downloadedBytes * 100) / totalBytes) : 0;
            this.error = error;
            this.complete = complete;
            this.filePath = filePath;
        }
    }

    @GetMapping("/check")
    public ResponseEntity<UpdateInfo> checkUpdate() {
        try {
            String current = getCleanVersion(appVersion);
            
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("User-Agent", "MyTwoCents-Updater")
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Erro ao consultar GitHub API: Status {}", response.statusCode());
                return ResponseEntity.badRequest().build();
            }

            String body = response.body();
            
            // Usando regex simples para extrair os campos necessários do JSON da API do GitHub
            // para não precisar adicionar novas dependências como Jackson ou GSON no pom.xml
            String latestTag = extractJsonField(body, "tag_name");
            String releaseNotes = extractJsonField(body, "body");
            String cleanLatest = getCleanVersion(latestTag);

            boolean hasUpdate = isNewerVersion(current, cleanLatest);

            UpdateInfo info = new UpdateInfo();
            info.setCurrentVersion("v" + current);
            info.setLatestVersion(latestTag);
            info.setReleaseNotes(releaseNotes != null ? releaseNotes.replace("\\r\\n", "\n").replace("\\n", "\n") : "");
            info.setUpdateAvailable(hasUpdate);

            // Determinar link de download baseado no SO
            setupDownloadUrl(body, info);

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Erro ao verificar atualização", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/progress")
    public ResponseEntity<DownloadStatus> getProgress() {
        boolean complete = downloadedFile != null && downloadedFile.exists() && !downloading && downloadError == null;
        String path = downloadedFile != null ? downloadedFile.getAbsolutePath() : null;
        return ResponseEntity.ok(new DownloadStatus(
                downloading, totalBytes, downloadedBytes, downloadError, complete, path
        ));
    }

    @PostMapping("/download")
    public ResponseEntity<String> startDownload(@RequestParam("url") String downloadUrl, @RequestParam("fileName") String fileName) {
        if (downloading) {
            return ResponseEntity.badRequest().body("Download já em andamento.");
        }

        downloading = true;
        downloadedBytes = 0;
        totalBytes = 0;
        downloadError = null;
        downloadedFile = null;

        CompletableFuture.runAsync(() -> {
            try {
                // Pasta de downloads padrão ou home do usuário como fallback
                String userHome = System.getProperty("user.home");
                File downloadsDir = new File(userHome + File.separator + "Downloads");
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }

                File targetFile = new File(downloadsDir, fileName);
                log.info("Iniciando download da atualização para: {}", targetFile.getAbsolutePath());

                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "MyTwoCents-Updater");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int responseCode = conn.getResponseCode();
                // Lida com redirecionamentos (comum no GitHub Releases)
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "MyTwoCents-Updater");
                    responseCode = conn.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Servidor retornou código HTTP " + responseCode);
                }

                totalBytes = conn.getContentLengthLong();

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(targetFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;
                    }
                }

                downloadedFile = targetFile;
                log.info("Download concluído com sucesso: {}", targetFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Erro ao baixar atualização", e);
                downloadError = e.getMessage();
            } finally {
                downloading = false;
            }
        });

        return ResponseEntity.ok("Download iniciado.");
    }

    @PostMapping("/apply")
    public ResponseEntity<String> applyUpdate() {
        if (downloadedFile == null || !downloadedFile.exists()) {
            return ResponseEntity.badRequest().body("Nenhum arquivo baixado encontrado.");
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            log.info("Aplicando atualização no OS: {}. Executando arquivo: {}", os, downloadedFile.getAbsolutePath());

            if (os.contains("win")) {
                // Windows: Executa o instalador .exe
                new ProcessBuilder("cmd.exe", "/c", "start", "", downloadedFile.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                // macOS: Abre o instalador .dmg
                new ProcessBuilder("open", downloadedFile.getAbsolutePath()).start();
            } else {
                // Linux: Executa xdg-open para abrir o pacote (.deb ou .rpm) no instalador nativo do sistema
                new ProcessBuilder("xdg-open", downloadedFile.getAbsolutePath()).start();
            }

            // Agenda o encerramento do app após 1 segundo para dar tempo do processo filho iniciar
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    log.info("Encerrando MyTwoCents para prosseguir com a atualização.");
                    System.exit(0);
                } catch (InterruptedException ignored) {}
            }).start();

            return ResponseEntity.ok("Instalador iniciado. A aplicação será fechada em breve.");
        } catch (Exception e) {
            log.error("Erro ao aplicar atualização", e);
            return ResponseEntity.internalServerError().body("Erro ao executar instalador: " + e.getMessage());
        }
    }

    // ─── MÉTODOS AUXILIARES ───────────────────────────────────────────────────

    private String getCleanVersion(String version) {
        if (version == null || version.isEmpty() || version.contains("@project.version@")) {
            return "0.0.1";
        }
        String clean = version.trim().toLowerCase();
        if (clean.startsWith("v")) {
            clean = clean.substring(1);
        }
        if (clean.contains("-")) {
            clean = clean.split("-")[0];
        }
        return clean;
    }

    private boolean isNewerVersion(String currentStr, String latestStr) {
        try {
            String[] currentParts = currentStr.split("\\.");
            String[] latestParts = latestStr.split("\\.");

            int length = Math.max(currentParts.length, latestParts.length);
            for (int i = 0; i < length; i++) {
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i].replaceAll("\\D", "")) : 0;
                int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i].replaceAll("\\D", "")) : 0;

                if (latestPart > currentPart) {
                    return true;
                } else if (currentPart > latestPart) {
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao comparar versões: {} vs {}", currentStr, latestStr, e);
        }
        return false;
    }

    private String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\":\\s*\"(.*?)\"(,|\r?\n|})");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Tentativa de buscar campo de texto longo/multiline (body do Release)
        if (field.equals("body")) {
            Pattern multilinePattern = Pattern.compile("\"body\":\\s*\"([\\s\\S]*?)\"\\s*,?\\s*\r?\n\\s*\"[a-zA-Z0-9_]+\":");
            Matcher multilineMatcher = multilinePattern.matcher(json);
            if (multilineMatcher.find()) {
                return multilineMatcher.group(1);
            }
        }
        return null;
    }

    private void setupDownloadUrl(String responseBody, UpdateInfo info) {
        String os = System.getProperty("os.name").toLowerCase();
        
        List<Asset> assets = parseAssets(responseBody);
        
        String targetExtension = ".exe"; // fallback padrão Windows
        if (os.contains("mac")) {
            targetExtension = ".dmg";
        } else if (os.contains("nux") || os.contains("nix")) {
            // Verifica se é baseado em RPM (Fedora/Nobara) ou DEB (Ubuntu/Debian)
            if (new File("/usr/bin/rpm").exists() || new File("/etc/fedora-release").exists()) {
                targetExtension = ".rpm";
            } else {
                targetExtension = ".deb";
            }
        }

        for (Asset asset : assets) {
            if (asset.name.toLowerCase().endsWith(targetExtension)) {
                info.setDownloadUrl(asset.browserDownloadUrl);
                info.setFileName(asset.name);
                return;
            }
        }

        // Fallback: se não achar a extensão exata, pega o primeiro asset disponível
        if (!assets.isEmpty()) {
            info.setDownloadUrl(assets.get(0).browserDownloadUrl);
            info.setFileName(assets.get(0).name);
        } else {
            // Fallback absoluto: página de releases
            info.setDownloadUrl("https://github.com/Zants96/fcontrol/releases");
            info.setFileName("MyTwoCents_Setup" + targetExtension);
        }
    }

    private List<Asset> parseAssets(String json) {
        List<Asset> assets = new ArrayList<>();
        // Encontra blocos de assets no JSON
        Pattern assetPattern = Pattern.compile("\\{\\s*\"url\":\\s*\"[^\"]+\",\\s*\"id\":\\s*\\d+,[\\s\\S]*?\"name\":\\s*\"([^\"]+)\",[\\s\\S]*?\"browser_download_url\":\\s*\"([^\"]+)\"\\s*\\}");
        Matcher matcher = assetPattern.matcher(json);
        while (matcher.find()) {
            Asset asset = new Asset();
            asset.name = matcher.group(1);
            asset.browserDownloadUrl = matcher.group(2);
            assets.add(asset);
        }
        return assets;
    }

    private static class Asset {
        String name;
        String browserDownloadUrl;
    }
}
