package br.com.lesnik.mytwocents.controller;

import br.com.lesnik.mytwocents.service.CryptoService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final DataSource dataSource;
    private final CryptoService cryptoService;

    public BackupController(DataSource dataSource, CryptoService cryptoService) {
        this.dataSource = dataSource;
        this.cryptoService = cryptoService;
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportBackup(@RequestHeader(value = "X-Backup-Password", required = false) String password) {
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        File tempSqlFile = null;
        try {
            tempSqlFile = File.createTempFile("backup", ".sql");
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SCRIPT TO '" + tempSqlFile.getAbsolutePath() + "'");
            }

            byte[] rawData = Files.readAllBytes(tempSqlFile.toPath());
            byte[] encryptedData = cryptoService.encrypt(rawData, password);

            ByteArrayResource resource = new ByteArrayResource(encryptedData);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mytwocents_backup.mtc")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(encryptedData.length)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        } finally {
            if (tempSqlFile != null && tempSqlFile.exists()) {
                tempSqlFile.delete();
            }
        }
    }

    @PostMapping("/import")
    public ResponseEntity<String> importBackup(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Backup-Password", required = false) String password) {
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("Senha não fornecida.");
        }

        File tempSqlFile = null;
        try {
            byte[] encryptedData = file.getBytes();
            byte[] decryptedData = cryptoService.decrypt(encryptedData, password);

            tempSqlFile = File.createTempFile("restore", ".sql");
            Files.write(tempSqlFile.toPath(), decryptedData);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP ALL OBJECTS");
                stmt.execute("RUNSCRIPT FROM '" + tempSqlFile.getAbsolutePath() + "'");
            }

            return ResponseEntity.ok("Backup restaurado com sucesso!");

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("padding") || errorMsg.contains("Given final block not properly padded"))) {
                return ResponseEntity.status(401).body("Senha incorreta! Não foi possível desencriptar o backup.");
            }
            return ResponseEntity.internalServerError().body("Falha ao restaurar o arquivo de backup. Verifique a integridade do arquivo.");
        } finally {
            if (tempSqlFile != null && tempSqlFile.exists()) {
                tempSqlFile.delete();
            }
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<String> resetDatabase(@RequestHeader(value = "X-Backup-Password", required = false) String password) {
        if (password == null || password.isBlank()) {
            return ResponseEntity.status(401).body("Senha de confirmação necessária para redefinir o banco de dados.");
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("TRUNCATE TABLE investimento_lancamento");
            stmt.execute("TRUNCATE TABLE ativo");
            stmt.execute("TRUNCATE TABLE lancamento");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");

            return ResponseEntity.ok("Base de dados zerada com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao zerar a base de dados.");
        }
    }
}
