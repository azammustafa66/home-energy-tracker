package com.leetjourney.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.leetjourney.alert_service.AlertServiceApplication;
import com.leetjourney.ingestion_service.IngestionServiceApplication;
import com.leetjourney.usage_service.UsageServiceApplication;
import jakarta.mail.internet.MimeMessage;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end pipeline test for ingestion -> Kafka -> usage -> InfluxDB, plus
 * the threshold-based alert path that goes usage -> Kafka -> alert -> SMTP.
 *
 * Design choice:
 *   - Shared infra is brought up via Testcontainers (Kafka, MySQL, InfluxDB) plus
 *     GreenMail as an in-JVM SMTP server.
 *   - The three pipeline services (ingestion, usage, alert) are launched in the
 *     same JVM as separate Spring Boot child contexts via
 *     {@link SpringApplicationBuilder}. This avoids needing three different
 *     @SpringBootTest setups and keeps lifecycle simple.
 *   - device-service/user-service are not booted; usage-service's outbound HTTP
 *     calls are stubbed with WireMock to satisfy the aggregation step.
 *
 * Flow:
 *   1. POST a high-watts reading to ingestion-service
 *   2. assert it lands as a row in InfluxDB
 *   3. trigger usage-service aggregation
 *   4. assert email captured by GreenMail SMTP
 */
@Testcontainers
@Tag("e2e")
class EnergyPipelineE2ETest {

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("home_energy_tracker")
                    .withUsername("root")
                    .withPassword("password");

    @SuppressWarnings("resource")
    static final GenericContainer<?> INFLUX =
            new GenericContainer<>(DockerImageName.parse("influxdb:2.7"))
                    .withExposedPorts(8086)
                    .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                    .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "admin")
                    .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "adminpass")
                    .withEnv("DOCKER_INFLUXDB_INIT_ORG", "leetjourney")
                    .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", "usage-bucket")
                    .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", "my-token")
                    .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    @RegisterExtension
    static final GreenMailExtension GREENMAIL =
            new GreenMailExtension(ServerSetupTest.SMTP)
                    .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
                    .withPerMethodLifecycle(false);

    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static ConfigurableApplicationContext ingestionCtx;
    static ConfigurableApplicationContext usageCtx;
    static ConfigurableApplicationContext alertCtx;

    static int ingestionPort;
    static InfluxDBClient influxClient;

    @BeforeAll
    static void startEverything() {
        KAFKA.start();
        MYSQL.start();
        INFLUX.start();
        WIREMOCK.start();

        String kafkaBs = KAFKA.getBootstrapServers();
        String influxUrl = "http://" + INFLUX.getHost() + ":" + INFLUX.getMappedPort(8086);

        // -- Ingestion service
        ingestionCtx = new SpringApplicationBuilder(IngestionServiceApplication.class)
                .properties(
                        "spring.application.name=ingestion-service",
                        "server.port=0",
                        "spring.kafka.bootstrap-servers=" + kafkaBs,
                        "spring.kafka.template.default-topic=energy-usage",
                        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
                        "simulation.endpoint=http://localhost:0/api/v1/ingestion",
                        "simulation.requests-per-interval=0",
                        "simulation.interval-ms=60000",
                        "simulation.parallel-threads=1"
                )
                .run();
        ingestionPort = ingestionCtx.getEnvironment()
                .getProperty("local.server.port", Integer.class, 0);

        // -- Usage service
        long devId = 9000L;
        long userId = 4242L;
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/device/" + devId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"id\":%d,\"name\":\"x\",\"type\":\"LIGHT\",\"location\":\"home\",\"userId\":%d}",
                                devId, userId))));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/user/" + userId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"id\":%d,\"name\":\"u\",\"surname\":\"s\",\"email\":\"e2e@example.com\"," +
                                "\"address\":\"a\",\"alerting\":true,\"energyAlertingThreshold\":100.0}",
                                userId))));

        usageCtx = new SpringApplicationBuilder(UsageServiceApplication.class)
                .properties(
                        "spring.application.name=usage-service",
                        "server.port=0",
                        "spring.kafka.bootstrap-servers=" + kafkaBs,
                        "spring.kafka.consumer.group-id=usage-service-e2e",
                        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
                        "spring.kafka.consumer.properties.spring.json.type.mapping=energyUsageEvent:com.leetjourney.kafka.event.EnergyUsageEvent",
                        "spring.kafka.consumer.auto-offset-reset=earliest",
                        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
                        "influx.url=" + influxUrl,
                        "influx.token=my-token",
                        "influx.org=leetjourney",
                        "influx.bucket=usage-bucket",
                        "device.service.url=" + WIREMOCK.baseUrl() + "/api/v1/device",
                        "user.service.url=" + WIREMOCK.baseUrl() + "/api/v1/user"
                )
                .run();

        // -- Alert service
        alertCtx = new SpringApplicationBuilder(AlertServiceApplication.class)
                .properties(
                        "spring.application.name=alert-service",
                        "server.port=0",
                        "spring.kafka.bootstrap-servers=" + kafkaBs,
                        "spring.kafka.consumer.group-id=alert-service-e2e",
                        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
                        "spring.kafka.consumer.properties.spring.json.type.mapping=alertingEvent:com.leetjourney.kafka.event.AlertingEvent",
                        "spring.kafka.consumer.auto-offset-reset=earliest",
                        "spring.datasource.url=" + MYSQL.getJdbcUrl(),
                        "spring.datasource.username=" + MYSQL.getUsername(),
                        "spring.datasource.password=" + MYSQL.getPassword(),
                        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                        "spring.jpa.hibernate.ddl-auto=update",
                        "spring.mail.host=" + ServerSetupTest.SMTP.getBindAddress(),
                        "spring.mail.port=" + ServerSetupTest.SMTP.getPort(),
                        "spring.mail.properties.mail.smtp.auth=false",
                        "spring.mail.properties.mail.smtp.starttls.enable=false"
                )
                .run();

        influxClient = InfluxDBClientFactory.create(influxUrl, "my-token".toCharArray(), "leetjourney");
    }

    @AfterAll
    static void stopEverything() {
        if (ingestionCtx != null) ingestionCtx.close();
        if (usageCtx != null) usageCtx.close();
        if (alertCtx != null) alertCtx.close();
        if (influxClient != null) influxClient.close();
        WIREMOCK.stop();
        INFLUX.stop();
        MYSQL.stop();
        KAFKA.stop();
    }

    @Test
    void postIngestion_propagatesThroughPipeline_andSendsEmail() throws Exception {
        long deviceId = 9000L;

        // 1. POST a high-watts reading
        String payload = String.format(
                "{\"deviceId\":%d,\"energyConsumed\":5000.0,\"timestamp\":\"%s\"}",
                deviceId, Instant.now().toString());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + ingestionPort + "/api/v1/ingestion"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(201);

        // 2. Influx row appears
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    String flux = String.format("""
                            from(bucket: "usage-bucket")
                              |> range(start: -10m)
                              |> filter(fn: (r) => r["_measurement"] == "energy_usage")
                              |> filter(fn: (r) => r["deviceId"] == "%d")
                            """, deviceId);
                    QueryApi q = influxClient.getQueryApi();
                    List<FluxTable> tables = q.query(flux, "leetjourney");
                    boolean ok = tables.stream()
                            .flatMap(t -> t.getRecords().stream())
                            .map(FluxRecord::getValue)
                            .filter(v -> v instanceof Number)
                            .anyMatch(v -> ((Number) v).doubleValue() == 5000.0);
                    assertThat(ok).as("InfluxDB row from ingestion").isTrue();
                });

        // 3. Trigger usage-service aggregation - the cron is too coarse for tests
        Object usageService = usageCtx.getBean("usageService");
        usageService.getClass().getMethod("aggregateDeviceEnergyUsage").invoke(usageService);

        // 4. Email captured
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    MimeMessage[] msgs = GREENMAIL.getReceivedMessages();
                    assertThat(msgs).hasSizeGreaterThanOrEqualTo(1);
                    assertThat(msgs[0].getAllRecipients()[0].toString())
                            .isEqualTo("e2e@example.com");
                    assertThat(msgs[0].getContent().toString())
                            .contains("Energy consumption threshold exceeded");
                });

        // sanity: payload was well-formed
        new ObjectMapper().readTree(payload);
    }
}
