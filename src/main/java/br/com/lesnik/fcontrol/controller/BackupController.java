package br.com.lesnik.fcontrol.controller;

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

    public BackupController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportBackup() {
        try {
            File tempFile = File.createTempFile("backup", ".sql");
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SCRIPT TO '" + tempFile.getAbsolutePath() + "'");
            }
            
            byte[] data = Files.readAllBytes(tempFile.toPath());
            tempFile.delete();

            ByteArrayResource resource = new ByteArrayResource(data);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fcontrol_backup.sql")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/import")
    public ResponseEntity<String> importBackup(@RequestParam("file") MultipartFile file) {
        try {
            File tempFile = File.createTempFile("restore", ".sql");
            file.transferTo(tempFile);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                // Remove tudo primeiro
                stmt.execute("DROP ALL OBJECTS");
                // Roda o script de restauracao
                stmt.execute("RUNSCRIPT FROM '" + tempFile.getAbsolutePath() + "'");
            }

            tempFile.delete();
            return ResponseEntity.ok("Backup restaurado com sucesso!");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erro ao restaurar backup: " + e.getMessage());
        }
    }
}
