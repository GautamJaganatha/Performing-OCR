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

    private String generateMetricsReport(Document document) {
        log.debug("Generating metrics report for document: {}", document.getReferenceNumber());
        return String.format(
                "Document Processing Complete\n\n" +
                        "Reference Number: %s\n" +
                        "Total Words: %d\n" +
                        "Top 10 Words: %s\n\n" +
                        "You can request this document again by sending an email with subject 'Request Document: %s'",
                document.getReferenceNumber(),
                document.getTotalWords(),
                document.getTopWords(),
                document.getReferenceNumber()
        );
    }
}
