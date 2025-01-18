//package com.ToOCR.OCR.service;
//
//import lombok.extern.slf4j.Slf4j;
//import org.apache.logging.log4j.LogManager;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import jakarta.annotation.PostConstruct;
//import org.springframework.mail.SimpleMailMessage;
//import org.springframework.mail.javamail.JavaMailSender;
//
//@Service
//@Slf4j
//public class EmailTestService {
//
////    private static final Logger logger = (Logger) LogManager.getLogger(EmailTestService.class);
//
//    @Autowired
//    private JavaMailSender mailSender;
//
//    @PostConstruct
//    public void testEmail() {
//        try {
//            SimpleMailMessage message = new SimpleMailMessage();
//            message.setFrom("gautam9135687@gmail.com");
//            message.setTo("gautamus1793@gmail.com");  // Send to yourself for testing
//            message.setSubject("Test Email from Spring Boot");
//            message.setText("This is a test email from your Spring Boot application.");
//
//            mailSender.send(message);
//            System.out.println("Test email sent successfully!");
//        } catch (Exception e) {
//            System.out.println("Failed to send test email:");
//            e.printStackTrace();
//        }
//    }
//}
//
