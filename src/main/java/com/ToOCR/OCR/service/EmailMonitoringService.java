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

    public EmailMonitoringService(DocumentProcessingService documentProcessingService,
                                  EmailService emailService,
                                  DocumentRepository documentRepository,
                                  ZipService zipService) {
        this.documentProcessingService = documentProcessingService;
        this.emailService = emailService;
        this.documentRepository = documentRepository;
        this.zipService = zipService;
    }

    @Scheduled(fixedDelay = 60000)
    public void monitorEmails() {
        log.info("Starting scheduled email monitoring");

        try (Store store = connectToEmail()) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            // Process unread messages
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
        } catch (Exception e) {
            log.error("Error in email monitoring: {}", e.getMessage(), e);
        }
    }

    private Store connectToEmail() throws MessagingException {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.host", "imap.gmail.com");
        props.setProperty("mail.imaps.port", "993");
        props.setProperty("mail.imaps.starttls.enable", "true");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", emailUsername, emailPassword);
        log.info("Successfully connected to email server");

        return store;
    }

    private void processEmail(Message message) throws MessagingException, IOException {
        String subject = message.getSubject();
        String clientEmail = InternetAddress.parse(message.getFrom()[0].toString())[0].getAddress();
        log.info("Processing email from: {}, Subject: {}", clientEmail, subject);

        try {
            if (isDocumentRequest(subject)) {
                String referenceNumber = subject.split(":")[1].trim();
                handleDocumentRequest(referenceNumber, clientEmail);
                return;
            }

            if (message.getContentType().contains("multipart")) {
                processMultipartMessage(message, clientEmail);
            }
        } catch (Exception e) {
            log.error("Error processing email: {}", e.getMessage(), e);
        }
    }

    private boolean isDocumentRequest(String subject) {
        if(subject != null && subject.toLowerCase().contains("request document:")){
            return true;
        }
        return false;
    }

    private void processMultipartMessage(Message message, String clientEmail) throws Exception {
        Multipart multipart = (Multipart) message.getContent();

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (isZipAttachment(bodyPart)) {
                processZipAttachment(bodyPart, clientEmail);
            }
        }
    }

    private boolean isZipAttachment(BodyPart bodyPart) throws MessagingException {
        return Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())
                && bodyPart.getFileName().toLowerCase().endsWith(".zip");
    }

    private void processZipAttachment(BodyPart bodyPart, String clientEmail) {
        try (TempFile zipFile = new TempFile("attachment_", ".zip");
             TempFile pdfFile = new TempFile("pdf_", ".pdf")) {

            saveAttachment(bodyPart, zipFile.getFile());
            log.info("ZIP file saved temporarily");

            pdfFile.setFile(zipService.extractPdfFromZip(zipFile.getFile()));
            log.info("PDF extracted successfully");

            documentProcessingService.processDocument(pdfFile.getFile(), clientEmail);
            log.info("PDF processed successfully");

        } catch (Exception e) {
            log.error("Error processing ZIP: {}", e.getMessage(), e);
        }
    }

    private void saveAttachment(BodyPart bodyPart, File outputFile) throws Exception {
        try (InputStream is = bodyPart.getInputStream();
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    private void handleDocumentRequest(String referenceNumber, String clientEmail) {
        log.info("Processing document request for reference: {}", referenceNumber);
        try {
            Document document = findDocument(referenceNumber);
            File ocrFile = validateOcrFile(document);
            sendDocument(clientEmail, referenceNumber, ocrFile);
        } catch (Exception e) {
            handleRequestError(clientEmail, referenceNumber, e);
        }
    }

    private Document findDocument(String referenceNumber) {
        return documentRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new RuntimeException("Document not found: " + referenceNumber));
    }

    private File validateOcrFile(Document document) {
        File ocrFile = new File(document.getOcrFileName());
        if (!ocrFile.exists()) {
            throw new RuntimeException("OCR file not found");
        }
        return ocrFile;
    }

    private void sendDocument(String clientEmail, String referenceNumber, File ocrFile) throws MessagingException {
        emailService.sendSecureEmail(
                clientEmail,
                "Requested Document - Ref: " + referenceNumber,
                "Here is your requested document with reference number: " + referenceNumber,
                ocrFile
        );
        log.info("Document sent successfully");
    }

    private void handleRequestError(String clientEmail, String referenceNumber, Exception e) {
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

    private static class TempFile implements AutoCloseable {
        private File file;

        public TempFile(String prefix, String suffix) throws IOException {
            this.file = File.createTempFile(prefix, suffix);
        }

        public File getFile() {
            return file;
        }

        public void setFile(File newFile) {
            if (file != null && file.exists()) {
                file.delete();
            }
            file = newFile;
        }

        @Override
        public void close() {
            if (file != null && file.exists()) {
                file.delete();
            }
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

