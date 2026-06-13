package com.leetjourney.ingestion_service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyUsageDtoTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void deserialize_fromJsonString_populatesFields() throws Exception {
        String json = """
                {
                  "deviceId": 5,
                  "energyConsumed": 1.23,
                  "timestamp": "2024-06-12T10:15:30Z"
                }
                """;

        EnergyUsageDto dto = objectMapper.readValue(json, EnergyUsageDto.class);

        assertThat(dto.deviceId()).isEqualTo(5L);
        assertThat(dto.energyConsumed()).isEqualTo(1.23);
        assertThat(dto.timestamp()).isEqualTo(Instant.parse("2024-06-12T10:15:30Z"));
    }

    @Test
    void serialize_writesTimestampAsIsoString() throws Exception {
        EnergyUsageDto dto = EnergyUsageDto.builder()
                .deviceId(9L)
                .energyConsumed(0.42)
                .timestamp(Instant.parse("2024-06-12T10:15:30Z"))
                .build();

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).contains("\"deviceId\":9");
        assertThat(json).contains("\"energyConsumed\":0.42");
        // @JsonFormat(STRING) forces ISO instant rendering
        assertThat(json).contains("\"timestamp\":\"2024-06-12T10:15:30Z\"");
    }

    @Test
    void builder_buildsRecordEquivalentToCanonicalConstructor() {
        Instant now = Instant.now();

        EnergyUsageDto built = EnergyUsageDto.builder()
                .deviceId(3L)
                .energyConsumed(0.99)
                .timestamp(now)
                .build();
        EnergyUsageDto direct = new EnergyUsageDto(3L, 0.99, now);

        assertThat(built).isEqualTo(direct);
        assertThat(built.hashCode()).isEqualTo(direct.hashCode());
    }
}
