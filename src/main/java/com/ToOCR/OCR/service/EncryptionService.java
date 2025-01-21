package com.ToOCR.OCR.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
@Service
public class EncryptionService {

    // Inject file paths of public and private keys using @Value annotation
    @Value("${publicKey.path}")
    private String publicKeyPath;

    @Value("${privateKey.path}")
    private String privateKeyPath;

    // Encrypt the message using the public key from a file
    public String encrypt(String message) throws Exception {
        PublicKey publicKey = getPublicKeyFromFile(publicKeyPath);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    // Decrypt the message using the private key from a file
    public String decrypt(String encryptedMessage) throws Exception {
        PrivateKey privateKey = getPrivateKeyFromFile(privateKeyPath);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }

    // Load the public key from a PEM file
    private PublicKey getPublicKeyFromFile(String publicKeyPath) throws Exception {
        byte[] keyBytes = loadKeyFromFile(publicKeyPath);
        String keyString = new String(keyBytes);

        // Clean the key (remove header/footer and newlines)
        keyString = keyString.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\n", "")
                .replaceAll("\r", "");

        // Decode the base64 string
        byte[] decoded = Base64.getDecoder().decode(keyString);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    // Load the private key from a PEM file
    private PrivateKey getPrivateKeyFromFile(String privateKeyPath) throws Exception {
        byte[] keyBytes = loadKeyFromFile(privateKeyPath);
        String keyString = new String(keyBytes);

        // Clean the key (remove header/footer and newlines)
        keyString = keyString.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\n", "")
                .replaceAll("\r", "");

        // Decode the base64 string
        byte[] decoded = Base64.getDecoder().decode(keyString);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    // Load the key (public or private) from a PEM file
    private byte[] loadKeyFromFile(String keyPath) throws IOException {
        File file = new File(keyPath);
        FileInputStream keyFile = new FileInputStream(file);
        byte[] keyBytes = new byte[(int) file.length()];
        keyFile.read(keyBytes);
        keyFile.close();
        return keyBytes;
    }
}
