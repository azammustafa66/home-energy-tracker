package com.demo.insightservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class InsightEmailService {

    private final JavaMailSender mailSender;

    @Value("${insight.from-address}")
    private String fromAddress;

    public boolean sendInsight(String to, String insight) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(to);
        msg.setSubject("Your 3-day energy insight");
        msg.setText(insight);
        try {
            mailSender.send(msg);
            log.info("Sent insight email to {}", to);
            return true;
        } catch (MailException e) {
            log.error("Failed to send insight email to {}", to, e);
            return false;
        }
    }
}
