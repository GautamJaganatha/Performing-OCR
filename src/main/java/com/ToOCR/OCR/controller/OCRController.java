package com.ToOCR.OCR.controller;

import com.ToOCR.OCR.service.OCRService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import net.sourceforge.tess4j.TesseractException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


@RestController
@RequestMapping("/ocr")
public class OCRController {
    private static final Logger log = LoggerFactory.getLogger(OCRController.class);

    private final OCRService ocrService;

    public OCRController(OCRService ocrService) {

        this.ocrService = ocrService;
    }

    @PostMapping("/image")
    public ResponseEntity<String> convertImage(@RequestParam("file") MultipartFile file) {
        try {
            String text = ocrService.performOCR((File) file);
            return ResponseEntity.ok(text);
        } catch (IOException | TesseractException e) {
            return ResponseEntity.badRequest().body("Error processing file: " + e.getMessage());
        }
    }

    @PostMapping("/pdf")
    public ResponseEntity<String> convertPdf(@RequestParam("file") MultipartFile file) {
        try {
            // Create a temporary file
            log.info("Received file: " + file.getOriginalFilename());
            Path tempFile = Files.createTempFile("uploaded-", ".pdf");

            // Write the MultipartFile to the temp file
            log.info("Writing file to temp file: " + tempFile);
            file.transferTo(tempFile.toFile());

            // Pass the temp file to the OCR service
            log.info("Passing temp file to OCR service");
            String text = ocrService.performOCR(tempFile.toFile());

            // Clean up the temporary file after processing (optional)
            log.info("Cleaning up temp file");
            tempFile.toFile().delete();

            return ResponseEntity.ok(text);
        } catch (IOException | TesseractException e) {
            return ResponseEntity.badRequest().body("Error processing file: " + e.getMessage());
        }
    }
}

