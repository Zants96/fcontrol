package br.com.lesnik.mytwocents.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class CryptoService {

    private final String algorithm = "AES";

    /**
     * Gera uma chave AES-256 a partir de uma senha fornecida pelo usuário.
     */
    private SecretKeySpec getSecretKey(String password) throws Exception {
        byte[] key = password.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        key = sha.digest(key);
        return new SecretKeySpec(key, algorithm);
    }

    public byte[] encrypt(byte[] data, String password) throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(password));
        return cipher.doFinal(data);
    }

    public byte[] decrypt(byte[] data, String password) throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(password));
        return cipher.doFinal(data);
    }
}
