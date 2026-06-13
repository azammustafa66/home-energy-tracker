package com.leetjourney.usage_service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.leetjourney.kafka.event.EnergyUsageEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: produce an EnergyUsageEvent on Kafka, verify the consumer
 * persists a point to InfluxDB. Infra wired by {@link UsageServiceTestcontainers}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class UsageConsumerIT extends UsageServiceTestcontainers {

    @Autowired
    private KafkaTemplate<String, EnergyUsageEvent> kafkaTemplate;

    @Autowired
    private InfluxDBClient influxDBClient;

    @Value("${influx.bucket}")
    private String bucket;

    @Value("${influx.org}")
    private String org;

    @Test
    void energyUsageEvent_isWrittenToInflux() {
        long deviceId = 7777L;
        EnergyUsageEvent event = EnergyUsageEvent.builder()
                .deviceId(deviceId)
                .energyConsumed(99.5)
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send("energy-usage", event);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    String flux = String.format("""
                            from(bucket: "%s")
                              |> range(start: -5m)
                              |> filter(fn: (r) => r["_measurement"] == "energy_usage")
                              |> filter(fn: (r) => r["deviceId"] == "%d")
                            """, bucket, deviceId);
                    QueryApi queryApi = influxDBClient.getQueryApi();
                    List<FluxTable> tables = queryApi.query(flux, org);
                    boolean found = tables.stream()
                            .flatMap(t -> t.getRecords().stream())
                            .map(FluxRecord::getValue)
                            .filter(v -> v instanceof Number)
                            .anyMatch(v -> ((Number) v).doubleValue() == 99.5);
                    assertThat(found).as("expected InfluxDB row for deviceId=%d", deviceId).isTrue();
                });
    }
}
