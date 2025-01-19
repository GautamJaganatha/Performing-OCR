package com.ToOCR.OCR.model;



import javax.persistence.*;

import lombok.Data;  // If using Lombok
import lombok.Generated;

import java.time.LocalDateTime;
@Entity
@Table(name = "documents")
@Data  // If using Lombok, this will generate getters/setters
public class Document {
//    strategy = GenerationType.IDENTITY
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String referenceNumber;
    private String originalFileName;
    private String ocrFileName;
    private String clientEmail;
    private LocalDateTime processedDate;
    private Long fileSize;

    @Lob
    @Column(length = 16777215)  // For MySQL MEDIUMTEXT
    private String ocrContent;

    // If not using Lombok, add getters and setters:
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getOcrFileName() {
        return ocrFileName;
    }

    public void setOcrFileName(String ocrFileName) {
        this.ocrFileName = ocrFileName;
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public void setClientEmail(String clientEmail) {
        this.clientEmail = clientEmail;
    }

    public LocalDateTime getProcessedDate() {
        return processedDate;
    }

    public void setProcessedDate(LocalDateTime processedDate) {
        this.processedDate = processedDate;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getOcrContent() {
        return ocrContent;
    }

    public void setOcrContent(String ocrContent) {
        this.ocrContent = ocrContent;
    }
}