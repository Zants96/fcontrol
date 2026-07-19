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
    public ResponseEntity<Resource> exportBackup(@RequestHeader("X-Backup-Password") String password) {
        try {
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            // 1. Gera o SQL puro em um arquivo temporário
            File tempSqlFile = File.createTempFile("backup", ".sql");
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SCRIPT TO '" + tempSqlFile.getAbsolutePath() + "'");
            }
            
            // 2. Lê os bytes e encripta com a senha do usuário
            byte[] rawData = Files.readAllBytes(tempSqlFile.toPath());
            byte[] encryptedData = cryptoService.encrypt(rawData, password);
            
            tempSqlFile.delete();

            ByteArrayResource resource = new ByteArrayResource(encryptedData);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mytwocents_backup.mtc")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(encryptedData.length)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/import")
    public ResponseEntity<String> importBackup(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-Backup-Password") String password) {
        try {
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body("Senha não fornecida.");
            }

            // 1. Recebe o arquivo encriptado e desencripta usando a senha
            byte[] encryptedData = file.getBytes();
            byte[] decryptedData = cryptoService.decrypt(encryptedData, password);

            // 2. Salva o SQL desencriptado em um arquivo temporário para o H2 rodar
            File tempSqlFile = File.createTempFile("restore", ".sql");
            Files.write(tempSqlFile.toPath(), decryptedData);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                // Remove tudo primeiro
                stmt.execute("DROP ALL OBJECTS");
                // Roda o script de restauracao
                stmt.execute("RUNSCRIPT FROM '" + tempSqlFile.getAbsolutePath() + "'");
            }

            tempSqlFile.delete();
            return ResponseEntity.ok("Backup restaurado com sucesso!");

        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("padding") || errorMsg.contains("Given final block not properly padded"))) {
                return ResponseEntity.status(401).body("Senha incorreta! Não foi possível desencriptar o backup.");
            }
            return ResponseEntity.internalServerError().body("Erro ao restaurar backup: " + errorMsg);
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<String> resetDatabase() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("TRUNCATE TABLE investimento_lancamento");
            stmt.execute("TRUNCATE TABLE ativo");
            stmt.execute("TRUNCATE TABLE lancamento");
            stmt.execute("TRUNCATE TABLE ai_config");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            
            return ResponseEntity.ok("Base de dados zerada com sucesso!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erro ao zerar base de dados: " + e.getMessage());
        }
    }
}
