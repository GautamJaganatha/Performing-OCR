package com.ToOCR.OCR.service;

import com.ToOCR.OCR.model.Document;
import com.ToOCR.OCR.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentProcessingService {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final OCRService ocrService;
    private final EmailService emailService;
    private final DocumentMetricsService metricsService;

    @Value("${document.storage.path}")
    private String storagePath;

    public DocumentProcessingService(DocumentRepository documentRepository, OCRService ocrService, EmailService emailService, DocumentMetricsService metricsService) {
        this.documentRepository = documentRepository;
        this.ocrService = ocrService;
        this.emailService = emailService;
        this.metricsService = metricsService;
    }

    public Document processDocument(File pdfFile, String clientEmail) throws Exception {
        log.info("Starting document processing for file: {}", pdfFile.getName());

        // Generate unique reference number
        String referenceNumber = UUID.randomUUID().toString();
        log.info("Generated reference number: {}", referenceNumber);

        try {
            // Perform OCR
            log.debug("Starting OCR processing");
            String ocrContent = ocrService.performOCR(pdfFile);
            log.debug("OCR processing completed, extracted {} characters", ocrContent.length());

            // Create OCR file
            String ocrFileName = referenceNumber + "_ocr.txt";
            File ocrFile = new File(storagePath + "/" + ocrFileName);


            File parentDir = ocrFile.getParentFile();
            if (!parentDir.exists()) {
                boolean dirsCreated = parentDir.mkdirs();
                if (!dirsCreated) {
                    log.error("Failed to create the directory: {}", parentDir.getAbsolutePath());
                    throw new RuntimeException("Failed to create directory for OCR file");
                }
            }

            log.debug("Creating OCR file at: {}", ocrFile.getAbsolutePath());

            try (FileWriter writer = new FileWriter(ocrFile)) {
                writer.write(ocrContent);
                log.debug("OCR content written to file successfully");
            }

            // Calculate metrics
            log.debug("Calculating document metrics");
            Map<String, Object> metrics = metricsService.calculateMetrics(ocrContent);
            log.debug("Metrics calculated: {}", metrics);

            // Create document record
            log.debug("Creating document record");
            Document document = new Document();
            document.setReferenceNumber(referenceNumber);
            document.setOriginalFileName(pdfFile.getName());
            document.setOcrFileName(ocrFile.getAbsolutePath());
            document.setClientEmail(clientEmail);
            document.setProcessedDate(LocalDateTime.now());
            document.setFileSize(pdfFile.length());
            document.setOcrContent(ocrContent);
            document.setTotalWords((Integer) metrics.get("totalWords"));
            document.setTopWords(new ObjectMapper().writeValueAsString(metrics.get("topWords")));

            // Save document
            log.info("Saving document to database");
            document = documentRepository.save(document);
            log.debug("Document saved with ID: {}", document.getId());

            // Send email
            log.info("Sending email notification to client: {}", clientEmail);
            emailService.sendEmailWithAttachment(
                    clientEmail,
                    "Document Processing Complete - Ref: " + referenceNumber,
                    generateMetricsReport(document),
                    ocrFile.getAbsolutePath()
            );
            log.info("Email sent successfully");

            log.info("Document processing completed successfully");
            return document;

        } catch (Exception e) {
            log.error("Error processing document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process document", e);
        }
    }


    public String generateMetricsReport(Document document) {
        log.debug("Generating metrics report for document: {}", document.getReferenceNumber());

        StringBuilder report = new StringBuilder();
        report.append("Document Details:\n\n");
        report.append("Reference Number: ").append(document.getReferenceNumber()).append("\n");
        report.append("Original File Name: ").append(document.getOriginalFileName()).append("\n");
        report.append("Processing Date: ").append(document.getProcessedDate()).append("\n");
        report.append("Total Words: ").append(document.getTotalWords()).append("\n");
        report.append("Top Words: ").append(document.getTopWords()).append("\n\n");
        report.append("To request this document again, send an email with subject 'Request Document: ")
                .append(document.getReferenceNumber()).append("'");

        log.debug("Generated metrics report successfully");
        return report.toString();
    }
}
