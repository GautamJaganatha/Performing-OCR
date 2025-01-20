package com.ToOCR.OCR.service;




import com.ToOCR.OCR.model.Document;
import com.ToOCR.OCR.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.search.FlagTerm;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
@Service
public class EmailMonitoringService {
    private static final Logger log = LoggerFactory.getLogger(EmailMonitoringService.class);

    private final DocumentProcessingService documentProcessingService;
    private final EmailService emailService;
    private final DocumentRepository documentRepository;

    @Value("${spring.mail.username}")
    private String emailUsername;

    @Value("${spring.mail.password}")
    private String emailPassword;

    public EmailMonitoringService(DocumentProcessingService documentProcessingService, EmailService emailService, DocumentRepository documentRepository) {
        this.documentProcessingService = documentProcessingService;
        this.emailService = emailService;
        this.documentRepository = documentRepository;
    }

    @Scheduled(fixedDelay = 60000) // Checks every 60 seconds
    public void monitorEmails() {
        log.info("Starting scheduled email monitoring");
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.host", "imap.gmail.com");
        props.setProperty("mail.imaps.port", "993");
        props.setProperty("mail.imaps.starttls.enable", "true");

        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            log.debug("Connecting to email server with username: {}", emailUsername);
            store.connect("imap.gmail.com", emailUsername, emailPassword);
            log.info("Successfully connected to email server");

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            log.debug("Opened INBOX folder in READ_WRITE mode");

            // Search for unread messages
            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            log.info("Found {} unread messages", messages.length);

            for (Message message : messages) {
                try {
                    processEmail(message);
                    message.setFlag(Flags.Flag.SEEN, true);
                } catch (Exception e) {
                    log.error("Error processing message: {}", e.getMessage(), e);
                }
            }

            inbox.close(false);
            store.close();
            log.info("Email monitoring cycle completed");
        } catch (Exception e) {
            log.error("Error in email monitoring: {}", e.getMessage(), e);
        }
    }

    private void processEmail(Message message) throws MessagingException, IOException {
        log.info("Processing new email message");
        String subject = message.getSubject();
        String from = message.getFrom()[0].toString();
        String clientEmail = InternetAddress.parse(from)[0].getAddress();
        log.debug("Email from: {}, Subject: {}", clientEmail, subject);

        try {
            if (subject != null && subject.toLowerCase().contains("request document:")) {
                String referenceNumber = subject.split(":")[1].trim();
                log.info("Document request received for reference: {}", referenceNumber);
                handleDocumentRequest(referenceNumber, clientEmail);
                return;
            }

            if (message.getContentType().contains("multipart")) {
                log.info("Processing new document submission");
                processNewDocument(message, clientEmail);
            }
        } catch (Exception e) {
            log.error("Error processing email: {}", e.getMessage(), e);
        }
    }

    private void processNewDocument(Message message, String clientEmail) throws MessagingException, IOException {
        log.info("Processing new document from: {}", clientEmail);
        Multipart multipart = (Multipart) message.getContent();

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                String fileName = bodyPart.getFileName();
                log.debug("Found attachment: {}", fileName);

                if (fileName.toLowerCase().endsWith(".pdf")) {
                    processAttachment(bodyPart, fileName, clientEmail);
                } else {
                    log.debug("Skipping non-PDF attachment: {}", fileName);
                }
            }
        }
    }

    private void processAttachment(BodyPart bodyPart, String fileName, String clientEmail) {
        log.info("Processing PDF attachment: {}", fileName);
        File tempFile = null;
        try {
            tempFile = File.createTempFile("pdf_", fileName);
            try (InputStream is = bodyPart.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            log.debug("PDF saved temporarily to: {}", tempFile.getAbsolutePath());

            documentProcessingService.processDocument(tempFile, clientEmail);
            log.info("Document processed successfully");
        } catch (Exception e) {
            log.error("Error processing attachment: {}", e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
                log.debug("Temporary file deleted");
            }
        }
    }
    private void handleDocumentRequest(String referenceNumber, String clientEmail) {
        log.info("Handling document request for reference: {}", referenceNumber);
        try {
            Document document = documentRepository.findByReferenceNumber(referenceNumber)
                    .orElseThrow(() -> new RuntimeException("Document not found: " + referenceNumber));
            log.debug("Document found in database");

            File ocrFile = new File(document.getOcrFileName());
            if (!ocrFile.exists()) {
                log.error("OCR file not found on filesystem: {}", document.getOcrFileName());
                throw new RuntimeException("OCR file not found");
            }


            log.info("Sending requested document to client");
            emailService.sendEmailWithAttachment(
                    clientEmail,
                    "Requested Document - Ref: " + referenceNumber,
                    documentProcessingService.generateMetricsReport(document),
                    ocrFile.getAbsolutePath()
            );
            log.info("Document sent successfully");
        } catch (Exception e) {
            log.error("Error handling document request: {}", e.getMessage(), e);
        }
    }

//    private String generateMetricsReport(Document document) {
//        log.debug("Generating metrics report for document: {}", document.getReferenceNumber());
//
//        StringBuilder report = new StringBuilder();
//        report.append("Document Details:\n\n");
//        report.append("Reference Number: ").append(document.getReferenceNumber()).append("\n");
//        report.append("Original File Name: ").append(document.getOriginalFileName()).append("\n");
//        report.append("Processing Date: ").append(document.getProcessedDate()).append("\n");
//        report.append("Total Words: ").append(document.getTotalWords()).append("\n");
//        report.append("Top Words: ").append(document.getTopWords()).append("\n\n");
//        report.append("To request this document again, send an email with subject 'Request Document: ")
//                .append(document.getReferenceNumber()).append("'");
//
//        log.debug("Generated metrics report successfully");
//        return report.toString();
//    }
}