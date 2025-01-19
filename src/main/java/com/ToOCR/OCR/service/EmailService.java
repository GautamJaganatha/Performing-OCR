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
    private final JavaMailSender emailSender;

    private static final Logger log = LoggerFactory.getLogger(EmailMonitoringService.class);


    public EmailService(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }
    public void sendEmailWithAttachment(String to, String subject, String text, String attachmentPath)
            throws MessagingException {
        log.info("Preparing to send email to: {}", to);
        log.debug("Attachment path: {}", attachmentPath);

        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        try {
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);

            FileSystemResource file = new FileSystemResource(new File(attachmentPath));
            helper.addAttachment(file.getFilename(), file);
            log.debug("Email prepared with attachment: {}", file.getFilename());

            emailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch ( MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }
}