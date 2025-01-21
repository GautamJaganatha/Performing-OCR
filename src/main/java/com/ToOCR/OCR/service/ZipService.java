package com.ToOCR.OCR.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;

@Service
public class ZipService {
    private static final Logger log = LoggerFactory.getLogger(ZipService.class);

    public static final String password = "mySecurePassword123";

    public File extractPdfFromZip(File zipFile) {
        log.info("Starting to extract PDF from ZIP file: {}", zipFile.getName());
        try {
            ZipFile secureZip = new ZipFile(zipFile, password.toCharArray());
            File extractDir = Files.createTempDirectory("extract_").toFile();
            log.debug("Created temporary directory for extraction: {}", extractDir.getAbsolutePath());

            secureZip.extractAll(extractDir.getAbsolutePath());
            log.debug("Extracted ZIP contents to temporary directory");

            // Get the first PDF file
            File[] files = extractDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            if (files != null && files.length > 0) {
                log.info("Successfully extracted PDF from ZIP");
                return files[0];
            }
            log.error("No PDF file found in ZIP");
            throw new IOException("No PDF found in ZIP file");
        } catch (Exception e) {
            log.error("Failed to extract PDF from ZIP: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract PDF", e);
        }
    }

    public File createSecureZip(File file) {
        log.info("Creating secure ZIP for file: {}", file.getName());
        try {
            File zipFile = File.createTempFile("secure_", ".zip");
            log.debug("Created temporary ZIP file: {}", zipFile.getAbsolutePath());

            ZipParameters parameters = new ZipParameters();
            parameters.setEncryptFiles(true);
            parameters.setEncryptionMethod(EncryptionMethod.AES);
            log.debug("Configured ZIP encryption parameters");



            ZipFile secureZip = new ZipFile(zipFile, password.toCharArray());
            secureZip.addFile(file, parameters);
            log.info("Successfully created encrypted ZIP file");

            return zipFile;
        } catch (Exception e) {
            log.error("Failed to create secure ZIP: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create secure ZIP", e);
        }
    }

    public String generatePassword() {
        log.debug("Generating secure password");
        String password = UUID.randomUUID().toString().substring(0, 12);
        log.debug("Generated password successfully");
        return password;
    }

    public void cleanup(File... files) {
        log.debug("Starting cleanup of temporary files");
        for (File file : files) {
            if (file != null && file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.debug("Successfully deleted file: {}", file.getAbsolutePath());
                } else {
                    log.warn("Failed to delete file: {}", file.getAbsolutePath());
                }
            }
        }
        log.debug("Cleanup completed");
    }
}
