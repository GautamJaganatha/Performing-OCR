package com.ToOCR.OCR.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String referenceNumber;
    private String originalFileName;
    private String ocrFileName;
    private String clientEmail;
    private LocalDateTime processedDate;
    private Long fileSize;
    private Integer pageCount;
    private String documentMetrics;

    @Lob
    private String ocrContent;
}

