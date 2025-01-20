package com.ToOCR.OCR.service;




import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender emailSender;
    private final ZipService zipService;

    public EmailService(JavaMailSender emailSender, ZipService zipService) {
        this.emailSender = emailSender;
        this.zipService = zipService;
    }

    public void sendSecureEmail(String to, String subject, String text, File file)
            throws MessagingException {
        log.info("Preparing to send secure email to: {}", to);
        File zipFile = null;
        try {
            // Generate password and create secure ZIP
            String password = zipService.generatePassword();
            zipFile = zipService.createSecureZip(file, password);
            log.debug("Created secure ZIP file");

            // Send ZIP file
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text + "\n\nPassword will be sent separately.");

            FileSystemResource fileResource = new FileSystemResource(zipFile);
            helper.addAttachment("document.zip", fileResource);

            emailSender.send(message);
            log.info("Sent ZIP file email successfully");

            String referenceNo = file.getAbsolutePath();

            // Send password separately
            sendPasswordEmail(to, password);

        } catch (Exception e) {
            log.error("Failed to send secure email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send secure email", e);
        } finally {
            if (zipFile != null && zipFile.exists()) {
                zipFile.delete();
                log.debug("Cleaned up temporary ZIP file");
            }
        }
    }

    private void sendPasswordEmail(String to, String password) throws MessagingException {
        log.info("Sending password email to: {}", to);
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false);

        helper.setTo(to);
        helper.setSubject("Password for Your Document");
        helper.setText("Your password is: " + password);

        emailSender.send(message);
        log.info("Password email sent successfully");
    }
}