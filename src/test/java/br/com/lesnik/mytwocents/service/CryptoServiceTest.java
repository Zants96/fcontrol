package br.com.lesnik.mytwocents.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private final CryptoService cryptoService = new CryptoService();

    @Test
    void testEncryptionDecryption() throws Exception {
        String originalText = "Dados ultra secretos do MyTwoCents";
        byte[] originalData = originalText.getBytes();
        String password = "senha-forte-123";

        byte[] encrypted = cryptoService.encrypt(originalData, password);
        assertNotNull(encrypted);
        assertNotEquals(originalText, new String(encrypted));

        byte[] decrypted = cryptoService.decrypt(encrypted, password);
        assertEquals(originalText, new String(decrypted));
    }

    @Test
    void testDecryptionWithWrongPassword() throws Exception {
        String originalText = "Dados";
        byte[] originalData = originalText.getBytes();
        String password = "senha-correta";
        String wrongPassword = "senha-errada";

        byte[] encrypted = cryptoService.encrypt(originalData, password);

        assertThrows(Exception.class, () -> {
            cryptoService.decrypt(encrypted, wrongPassword);
        });
    }
}
