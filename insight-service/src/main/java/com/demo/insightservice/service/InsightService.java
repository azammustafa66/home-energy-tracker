package com.demo.insightservice.service;

import com.demo.common.kafka.event.EnergyAlertEvent;
import com.demo.insightservice.entity.BreachedUser;
import com.demo.insightservice.repository.BreachedUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class InsightService {

    private final BreachedUserRepository breachedUserRepository;

    @KafkaListener(topics = "threshold-breaches", groupId = "insight-service")
    @Transactional
    public void consumeBreach(EnergyAlertEvent event) {
        log.info("Recording breach for user={} consumption={} threshold={}",
                event.userId(), event.consumption(), event.threshold());
        breachedUserRepository.save(BreachedUser.builder()
                .userId(event.userId())
                .email(event.email())
                .build());
    }
}
