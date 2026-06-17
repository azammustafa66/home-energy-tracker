package com.demo.alertservice.service;

import com.demo.common.kafka.event.EnergyAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private final EmailService emailService;

    @KafkaListener(topics = "threshold-breaches", groupId = "alert-service")
    public void consumeEnergyConsumptionAlertEvent(EnergyAlertEvent event) {
        log.info("Consuming energy consumption alert: {}", event);
        emailService.sendBreachAlert(event);
    }
}
