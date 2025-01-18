package com.ToOCR.OCR.service;

import com.ToOCR.OCR.model.Document;
import com.ToOCR.OCR.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DocumentProcessingService {
    private final DocumentRepository documentRepository;
    private final OCRService ocrService;
    private final EmailService emailService;

    @Value("${document.storage.path}")
    private String storagePath;

    public DocumentProcessingService(DocumentRepository documentRepository, OCRService ocrService, EmailService emailService) {
        this.documentRepository = documentRepository;
        this.ocrService = ocrService;
        this.emailService = emailService;
    }

    public Document processDocument(File pdfFile, String clientEmail) throws Exception {
        // Generate unique reference number
        String referenceNumber = UUID.randomUUID().toString();

        // Perform OCR
        String ocrContent = ocrService.performOCR(pdfFile);

        // Create OCR file
        String ocrFileName = referenceNumber + "_ocr.txt";
        File ocrFile = new File(storagePath + "/" + ocrFileName);
        // Write OCR content to file...

        // Create document record
        Document document = new Document();
        document.setReferenceNumber(referenceNumber);
        document.setOriginalFileName(pdfFile.getName());
        document.setOcrFileName(ocrFileName);
        document.setClientEmail(clientEmail);
        document.setProcessedDate(LocalDateTime.now());
        document.setFileSize(pdfFile.length());
        document.setOcrContent(ocrContent);

        // Save document
        document = documentRepository.save(document);

        // Send email with OCR result
        emailService.sendEmailWithAttachment(
                clientEmail,
                "Document Processing Complete - Ref: " + referenceNumber,
                "Your document has been processed. Reference number: " + referenceNumber,
                ocrFile.getAbsolutePath()
        );

        return document;
    }

    public Document getDocumentByReference(String referenceNumber) {
        return documentRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }
}
