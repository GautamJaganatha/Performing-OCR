package com.ToOCR.OCR.repository;

import com.ToOCR.OCR.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByReferenceNumber(String referenceNumber);
}
