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
    private final ZipService zipService;

    @Value("${spring.mail.username}")
    private String emailUsername;

    @Value("${spring.mail.password}")
    private String emailPassword;

    public EmailMonitoringService(DocumentProcessingService documentProcessingService, EmailService emailService, DocumentRepository documentRepository, ZipService zipService) {
        this.documentProcessingService = documentProcessingService;
        this.emailService = emailService;
        this.documentRepository = documentRepository;
        this.zipService = zipService;
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

            // First check if this is a document request
            if (subject != null && subject.toLowerCase().contains("request document:")) {
                String referenceNumber = subject.split(":")[1].trim();
                log.info("Document request received for reference: {}", referenceNumber);
                handleDocumentRequest(referenceNumber, clientEmail);
                return;  // Exit after handling document request
            }

            // Get email content and check for password
            String content = getMessageContent(message);
            log.debug("Email content received: {}", content != null ? "Yes" : "No");
            if (content != null) {
                log.debug("Content length: {}", content.length());
            }


            if (message.getContentType().contains("multipart")) {
                log.info("Found multipart content, checking for ZIP attachment");
                Multipart multipart = (Multipart) message.getContent();
                log.debug("Multipart count: {}", multipart.getCount());

                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart bodyPart = multipart.getBodyPart(i);
                    String disposition = bodyPart.getDisposition();
                    log.debug("Part {} disposition: {}", i, disposition);

                    if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
                        String fileName = bodyPart.getFileName();
                        log.debug("Found attachment: {}", fileName);

                        if (fileName.toLowerCase().endsWith(".zip")) {
                            log.info("Processing ZIP attachment with password");
                            processZipAttachment(bodyPart, clientEmail);
                        } else {
                            log.warn("Skipping attachment - ZIP: {}, Password found: {}",
                                    fileName.toLowerCase().endsWith(".zip"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing email: {}", e.getMessage(), e);
            log.error("Stack trace:", e);
        }
    }


    private String getMessageContent(Message message) throws IOException, MessagingException {
        log.debug("Extracting message content");

        if (message.getContent() instanceof Multipart) {
            Multipart multipart = (Multipart) message.getContent();
            log.debug("Processing multipart message with {} parts", multipart.getCount());

            // First try to find text content
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String contentType = bodyPart.getContentType().toLowerCase();
                log.debug("Part {} content type: {}", i, contentType);

                // Check for text content
                if (contentType.contains("text/plain") || contentType.contains("text/html")) {
                    Object content = bodyPart.getContent();
                    if (content != null) {
                        String textContent = content.toString();
                        log.debug("Found text content: {}", textContent);
                        return textContent;
                    }
                }

                // If part is multipart, check its parts too
                if (bodyPart.getContent() instanceof Multipart) {
                    String nestedContent = getNestedContent((Multipart) bodyPart.getContent());
                    if (nestedContent != null) {
                        return nestedContent;
                    }
                }
            }
        } else {
            String content = message.getContent().toString();
            log.debug("Found direct content in email");
            return content;
        }

        log.warn("No text content found in email");
        return null;
    }

    private String getNestedContent(Multipart multipart) throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            String contentType = bodyPart.getContentType().toLowerCase();

            if (contentType.contains("text/plain") || contentType.contains("text/html")) {
                Object content = bodyPart.getContent();
                if (content != null) {
                    return content.toString();
                }
            }
        }
        return null;
    }

    private void processZipAttachment(BodyPart bodyPart, String clientEmail) {
        log.info("Starting to process ZIP attachment");
        File zipFile = null;
        File pdfFile = null;

        try {
            // Save ZIP file
            zipFile = saveAttachment(bodyPart);
            log.info("ZIP file saved temporarily at: {}", zipFile.getAbsolutePath());

            // Extract PDF
            pdfFile = zipService.extractPdfFromZip(zipFile);
            log.info("PDF extracted successfully: {}", pdfFile.getName());

            // Process PDF
            documentProcessingService.processDocument(pdfFile, clientEmail);
            log.info("PDF processed successfully");

        } catch (Exception e) {
            log.error("Error processing ZIP: {}", e.getMessage(), e);
        } finally {
            // Cleanup
            if (zipFile != null && zipFile.exists()) {
                zipFile.delete();
                log.debug("Temporary ZIP file deleted");
            }
            if (pdfFile != null && pdfFile.exists()) {
                pdfFile.delete();
                log.debug("Temporary PDF file deleted");
            }
        }
    }

    private File saveAttachment(BodyPart bodyPart) throws Exception {
        File tempFile = File.createTempFile("attachment_", ".zip");
        try (InputStream is = bodyPart.getInputStream();
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
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
            emailService.sendSecureEmail(
                    clientEmail,
                    "Requested Document - Ref: " + referenceNumber,
                    "Here is your requested document with reference number: " + referenceNumber,
                    ocrFile
            );
            log.info("Document sent successfully");
        } catch (Exception e) {
            log.error("Error handling document request: {}", e.getMessage(), e);
            try {
                emailService.sendSimpleEmail(
                        clientEmail,
                        "Error Processing Document Request",
                        "Failed to retrieve document with reference: " + referenceNumber
                );
            } catch (Exception ee) {
                log.error("Failed to send error notification: {}", ee.getMessage());
            }
        }
    }
//    private String extractPasswordFromContent(Message message) throws IOException, MessagingException {
//        String content = getMessageContent(message);
//        if (content != null && content.contains("password :")) {
//            String[] parts = content.split("password :");
//            if (parts.length > 1) {
//                return parts[1].trim().split("\\s+")[0];
//            }
//        }
//        return null;
//    }
//    private void processNewDocument(Message message, String password, String clientEmail)
//            throws MessagingException, IOException {
//        log.info("Processing new document from: {}", clientEmail);
//        Multipart multipart = (Multipart) message.getContent();
//
//        for (int i = 0; i < multipart.getCount(); i++) {
//            BodyPart bodyPart = multipart.getBodyPart(i);
//            if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
//                String fileName = bodyPart.getFileName();
//                if (fileName.toLowerCase().endsWith(".zip")) {
//                    processZipAttachment(bodyPart, clientEmail);
//                }
//            }
//        }
//    }
}
