package com.demo.ingestionservice.service;

import com.demo.ingestionservice.dto.EnergyUsageDTO;
import com.demo.common.kafka.event.EnergyUsageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final KafkaTemplate<String, EnergyUsageEvent> kafkaTemplate;

    public void ingestEnergyUsage(EnergyUsageDTO usageDTO) {
        EnergyUsageEvent event = EnergyUsageEvent.builder().deviceId(usageDTO.deviceId()).energyConsumed(usageDTO.energyConsumed()).timestamp(usageDTO.timestamp()).build();

        kafkaTemplate.send("energy-usage", usageDTO.deviceId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish energy-usage event for device {}", event.deviceId(), ex);
                    } else {
                        log.info("Published energy-usage event for device {} to {}",
                                event.deviceId(), result.getRecordMetadata());
                    }
                });
    }
}
