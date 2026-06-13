package com.leetjourney.usage_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.leetjourney.kafka.event.AlertingEvent;
import com.leetjourney.usage_service.service.UsageService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: writes Influx data, stubs device/user services, then invokes the
 * aggregation method directly and asserts an alert lands on {@code energy-alerts}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class UsageAlertProducerIT extends UsageServiceTestcontainers {

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void wiremockProps(DynamicPropertyRegistry registry) {
        registry.add("device.service.url", () -> WIREMOCK.baseUrl() + "/api/v1/device");
        registry.add("user.service.url", () -> WIREMOCK.baseUrl() + "/api/v1/user");
    }

    @Autowired
    private InfluxDBClient influxDBClient;

    @Autowired
    private UsageService usageService;

    @Value("${influx.bucket}")
    private String bucket;

    @Value("${influx.org}")
    private String org;

    @Test
    void highWattsTriggersAlert_onEnergyAlertsTopic() throws Exception {
        long deviceId = 9001L;
        long userId = 4242L;

        // Seed influx with high-watt point (> threshold)
        Point p = Point.measurement("energy_usage")
                .addTag("deviceId", String.valueOf(deviceId))
                .addField("energyConsumed", 5000.0)
                .time(Instant.now().minusSeconds(10), WritePrecision.MS);
        influxDBClient.getWriteApiBlocking().writePoint(bucket, org, p);

        // Stub device-service: device → user mapping
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/device/" + deviceId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"id\":%d,\"name\":\"d\",\"type\":\"OTHER\",\"location\":\"home\",\"userId\":%d}",
                                deviceId, userId))));

        // Stub user-service: user with alerting enabled & low threshold
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/user/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"id\":%d,\"name\":\"u\",\"surname\":\"s\",\"email\":\"u@example.com\"," +
                                "\"address\":\"a\",\"alerting\":true,\"energyAlertingThreshold\":100.0}",
                                userId))));

        // Subscribe before triggering to avoid race
        try (Consumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(Collections.singletonList("energy-alerts"));
            // initial poll to assign partitions
            consumer.poll(Duration.ofSeconds(1));

            usageService.aggregateDeviceEnergyUsage();

            ConsumerRecord<String, String> record = pollOne(consumer, Duration.ofSeconds(20));
            assertThat(record).as("expected one energy-alerts record").isNotNull();

            AlertingEvent event = new ObjectMapper().readValue(record.value(), AlertingEvent.class);
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.email()).isEqualTo("u@example.com");
            assertThat(event.energyConsumed()).isGreaterThan(100.0);
        }
    }

    private Consumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        props.put("group.id", "usage-alert-it-" + System.nanoTime());
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
