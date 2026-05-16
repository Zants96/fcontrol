package br.com.lesnik.mytwocents.controller;

import br.com.lesnik.mytwocents.service.CryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BackupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CryptoService cryptoService;

    @Test
    void testFullBackupCycle() throws Exception {
        String password = "test-password";

        // 1. Testa Exportação
        byte[] encryptedBackup = mockMvc.perform(get("/api/backup/export")
                .header("X-Backup-Password", password))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=mytwocents_backup.mtc"))
                .andReturn().getResponse().getContentAsByteArray();

        // 2. Testa Importação com Senha Correta
        MockMultipartFile file = new MockMultipartFile("file", "test.mtc", "application/octet-stream", encryptedBackup);
        
        mockMvc.perform(multipart("/api/backup/import")
                .file(file)
                .header("X-Backup-Password", password))
                .andExpect(status().isOk())
                .andExpect(content().string("Backup restaurado com sucesso!"));

        // 3. Testa Importação com Senha Errada
        mockMvc.perform(multipart("/api/backup/import")
                .file(file)
                .header("X-Backup-Password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Senha incorreta! Não foi possível desencriptar o backup."));
    }
}
