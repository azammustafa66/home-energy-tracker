package com.demo.alertservice.service;

import com.demo.alertservice.entity.Alert;
import com.demo.alertservice.repository.AlertRepository;
import com.demo.common.kafka.event.EnergyAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final AlertRepository alertRepository;

    @Value("${alert.from-address}")
    private String fromAddress;

    @Async
    public void sendBreachAlert(EnergyAlertEvent event) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(event.email());
        msg.setSubject("Energy consumption alert");
        msg.setText(String.format(
                "Your energy consumption of %.2f kWh between %s and %s exceeded your threshold of %.2f kWh.",
                event.consumption(),
                event.windowStart(),
                event.windowEnd(),
                event.threshold()
        ));

        try {
            mailSender.send(msg);
            final Alert alert = buildAlert(true, msg.getText(), event.userId());
            alertRepository.saveAndFlush(alert);
            log.info("Sent email to {}", (Object) msg.getTo());
        } catch (MailException e) {
            final Alert alert = buildAlert(false, msg.getText(), event.userId());
            alertRepository.saveAndFlush(alert);
            log.error("Error sending email to {}", msg.getTo(), e);
        }
    }

    private Alert buildAlert(boolean sent, String message, UUID userId) {
        return Alert.builder().sent(sent).message(message).userId(userId).build();
    }
}
