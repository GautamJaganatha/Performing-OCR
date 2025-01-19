package com.ToOCR.OCR.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;



@Service
public class OCRService {
    private static final Logger log = LoggerFactory.getLogger(OCRService.class);

    private final Tesseract tesseract;

    public OCRService() {
        tesseract = new Tesseract();
        String datapath = "/usr/local/share/tessdata/";
        tesseract.setDatapath(datapath);
        tesseract.setLanguage("eng");

        log.info("Tesseract initialized with datapath: {}", datapath);
    }

    public String performOCR(File pdfFile) throws IOException, TesseractException {
        log.info("Starting OCR processing for file: {}", pdfFile.getName());
        StringBuilder ocrText = new StringBuilder();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            log.debug("PDF has {} pages", pageCount);

            for (int page = 0; page < pageCount; page++) {
                log.debug("Processing page {}/{}", page + 1, pageCount);
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300);
                String pageText = tesseract.doOCR(image);
                ocrText.append(pageText).append("\n");
                log.debug("Completed OCR for page {}", page + 1);
            }
        }

        log.info("OCR processing completed");
        return ocrText.toString();
    }
}