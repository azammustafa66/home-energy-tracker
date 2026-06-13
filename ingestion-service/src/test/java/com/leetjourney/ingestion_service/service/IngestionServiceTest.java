package com.leetjourney.ingestion_service.service;

import com.leetjourney.ingestion_service.dto.EnergyUsageDto;
import com.leetjourney.kafka.event.EnergyUsageEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private KafkaTemplate<String, EnergyUsageEvent> kafkaTemplate;

    @InjectMocks
    private IngestionService ingestionService;

    @Test
    void ingestEnergyUsage_publishesEventToEnergyUsageTopic() {
        Instant ts = Instant.parse("2024-06-12T10:15:30Z");
        EnergyUsageDto dto = EnergyUsageDto.builder()
                .deviceId(42L)
                .energyConsumed(1.75)
                .timestamp(ts)
                .build();

        ingestionService.ingestEnergyUsage(dto);

        ArgumentCaptor<EnergyUsageEvent> eventCaptor =
                ArgumentCaptor.forClass(EnergyUsageEvent.class);
        verify(kafkaTemplate).send(eq("energy-usage"), eventCaptor.capture());

        EnergyUsageEvent captured = eventCaptor.getValue();
        assertThat(captured.deviceId()).isEqualTo(42L);
        assertThat(captured.energyConsumed()).isEqualTo(1.75);
        assertThat(captured.timestamp()).isEqualTo(ts);
    }

    @Test
    void ingestEnergyUsage_mapsAllDtoFieldsOntoEvent() {
        Instant ts = Instant.now();
        EnergyUsageDto dto = EnergyUsageDto.builder()
                .deviceId(7L)
                .energyConsumed(0.0)
                .timestamp(ts)
                .build();

        ingestionService.ingestEnergyUsage(dto);

        ArgumentCaptor<EnergyUsageEvent> captor = ArgumentCaptor.forClass(EnergyUsageEvent.class);
        verify(kafkaTemplate).send(eq("energy-usage"), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(EnergyUsageEvent.builder()
                        .deviceId(7L)
                        .energyConsumed(0.0)
                        .timestamp(ts)
                        .build());
    }

    @Test
    void ingestEnergyUsage_publishesExactlyOnce() {
        EnergyUsageDto dto = EnergyUsageDto.builder()
                .deviceId(1L)
                .energyConsumed(0.5)
                .timestamp(Instant.now())
                .build();

        ingestionService.ingestEnergyUsage(dto);

        verify(kafkaTemplate, org.mockito.Mockito.times(1))
                .send(eq("energy-usage"), org.mockito.ArgumentMatchers.any(EnergyUsageEvent.class));
        org.mockito.Mockito.verifyNoMoreInteractions(kafkaTemplate);
    }
}
