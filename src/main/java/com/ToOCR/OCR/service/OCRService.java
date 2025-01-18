package com.ToOCR.OCR.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;


@Service
public class OCRService {
    private static final Logger logger = LoggerFactory.getLogger(OCRService.class);

    private final Tesseract tesseract;

    public OCRService() {
        tesseract = new Tesseract();
        String datapath = "/usr/local/share/tessdata/";
        tesseract.setDatapath(datapath);
        tesseract.setLanguage("eng");

        logger.info("Tesseract initialized with datapath: {}", datapath);
    }

    public String performOCR(File pdfFile) throws IOException, TesseractException {
        StringBuilder ocrText = new StringBuilder();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300);
                String pageText = tesseract.doOCR(image);
                ocrText.append(pageText).append("\n");
            }
        }

        return ocrText.toString();
    }
}
