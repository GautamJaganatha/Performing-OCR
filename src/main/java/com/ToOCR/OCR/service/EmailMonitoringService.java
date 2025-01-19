package com.ToOCR.OCR.service;




import lombok.extern.slf4j.Slf4j;
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
    private final DocumentProcessingService documentProcessingService;

    private static final Logger log = LoggerFactory.getLogger(EmailMonitoringService.class);


    @Value("${spring.mail.username}")
    private String emailUsername;

    @Value("${spring.mail.password}")
    private String emailPassword;

    public EmailMonitoringService(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }

    @Value("${document.storage.path}")
    private String storagePath;

    @Scheduled(fixedDelay = 60000)
    public void monitorEmails() {
        log.info("Starting email monitoring cycle");
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

            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            log.info("Found {} unread messages", messages.length);

            for (Message message : messages) {
                try {
                    log.debug("Processing message with subject: {}", message.getSubject());
                    processEmail(message);
                    message.setFlag(Flags.Flag.SEEN, true);
                    log.debug("Successfully processed message and marked as seen");
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
        String from = message.getFrom()[0].toString();
        String clientEmail = InternetAddress.parse(from)[0].getAddress();
        log.info("Processing email from: {}", clientEmail);

        if (message.getContentType().contains("multipart")) {
            log.debug("Message contains multipart content");
            Multipart multipart = (Multipart) message.getContent();

            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                log.debug("Processing body part {}/{}", i + 1, multipart.getCount());

                if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    String fileName = bodyPart.getFileName();
                    log.info("Found attachment: {}", fileName);

                    if (fileName.toLowerCase().endsWith(".pdf")) {
                        processPdfAttachment(bodyPart, fileName, clientEmail);
                    } else {
                        log.debug("Skipping non-PDF attachment: {}", fileName);
                    }
                }
            }
        } else {
            log.debug("Message does not contain multipart content");
        }
    }

    private void processPdfAttachment(BodyPart bodyPart, String fileName, String clientEmail) {
        File tempFile = new File(storagePath + "/temp_" + fileName);
        log.info("Processing PDF attachment: {}", fileName);

        try (InputStream is = bodyPart.getInputStream();
             FileOutputStream fos = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            log.debug("Temporarily saved PDF to: {}", tempFile.getAbsolutePath());

            documentProcessingService.processDocument(tempFile, clientEmail);
            log.info("Successfully processed PDF document");
        } catch (Exception e) {
            log.error("Error processing PDF attachment: {}", e.getMessage(), e);
        } finally {
            if (tempFile.exists() && tempFile.delete()) {
                log.debug("Cleaned up temporary file: {}", tempFile.getAbsolutePath());
            }
        }
    }
}