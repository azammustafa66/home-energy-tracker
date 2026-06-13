package com.leetjourney.ingestion_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetjourney.ingestion_service.dto.EnergyUsageDto;
import com.leetjourney.kafka.event.EnergyUsageEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: POSTs to the ingestion controller and verifies the message
 * lands on the {@code energy-usage} Kafka topic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class IngestionControllerIT {

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @BeforeAll
    static void startKafka() {
        KAFKA.start();
    }

    @AfterAll
    static void stopKafka() {
        KAFKA.stop();
    }

    @DynamicPropertySource
    static void registerKafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postIngestion_publishesEnergyUsageEventToKafka() throws Exception {
        EnergyUsageDto dto = EnergyUsageDto.builder()
                .deviceId(42L)
                .energyConsumed(123.45)
                .timestamp(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        mockMvc.perform(post("/api/v1/ingestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        try (Consumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(Collections.singletonList("energy-usage"));
            ConsumerRecord<String, String> record = pollOne(consumer, Duration.ofSeconds(15));
            assertThat(record).as("expected one energy-usage record").isNotNull();

            EnergyUsageEvent event = objectMapper.readValue(record.value(), EnergyUsageEvent.class);
            assertThat(event.deviceId()).isEqualTo(42L);
            assertThat(event.energyConsumed()).isEqualTo(123.45);
            assertThat(event.timestamp()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        }
    }

    private Consumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        props.put("group.id", "ingestion-it-" + System.nanoTime());
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }

    private ConsumerRecord<String, String> pollOne(Consumer<String, String> consumer, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            Iterator<ConsumerRecord<String, String>> it = records.iterator();
            if (it.hasNext()) return it.next();
        }
        return null;
    }
}
