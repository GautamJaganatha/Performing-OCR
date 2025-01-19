package com.ToOCR.OCR.service;

import com.ToOCR.OCR.model.Document;
import com.ToOCR.OCR.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DocumentProcessingService {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);


    private final OCRService ocrService;
    private final EmailService emailService;
    private final DocumentRepository documentRepository;

    @Value("${document.storage.path}")
    private  String storagePath;

    public DocumentProcessingService(OCRService ocrService, EmailService emailService, DocumentRepository documentRepository) {
        this.ocrService = ocrService;
        this.emailService = emailService;
        this.documentRepository = documentRepository;
    }

    public Document processDocument(File pdfFile, String clientEmail) throws Exception {
        log.info("Starting document processing for file: {}", pdfFile.getName());

        String referenceNumber = UUID.randomUUID().toString();
        log.debug("Generated reference number: {}", referenceNumber);

        log.info("Starting OCR processing");
        String ocrContent = ocrService.performOCR(pdfFile);
        log.debug("OCR processing completed, extracted {} characters", ocrContent.length());

        String ocrFileName = referenceNumber + "_ocr.txt";
        File ocrFile = new File(storagePath + "/" + ocrFileName);
        log.debug("Creating OCR file at: {}", ocrFile.getAbsolutePath());

        try (FileWriter writer = new FileWriter(ocrFile)) {
            writer.write(ocrContent);
            log.debug("Successfully wrote OCR content to file");
        }

        Document document = new Document();
        document.setReferenceNumber(referenceNumber);
        document.setOriginalFileName(pdfFile.getName());
        document.setOcrFileName(ocrFileName);
        document.setClientEmail(clientEmail);
        document.setProcessedDate(LocalDateTime.now());
        document.setFileSize(pdfFile.length());
        document.setOcrContent(ocrContent);

        log.info("Saving document to database");
        document = documentRepository.save(document);
        log.debug("Document saved with ID: {}", document.getId());

        log.info("Sending email notification to: {}", clientEmail);
        emailService.sendEmailWithAttachment(
                clientEmail,
                "Document Processing Complete - Ref: " + referenceNumber,
                "Your document has been processed. Reference number: " + referenceNumber,
                ocrFile.getAbsolutePath()
        );
        log.info("Document processing completed successfully");

        return document;
    }


    public Document getDocumentByReference(String referenceNumber) {
        return documentRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }
}
