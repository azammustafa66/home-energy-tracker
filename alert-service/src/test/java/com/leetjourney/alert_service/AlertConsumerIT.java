package com.leetjourney.alert_service;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.leetjourney.alert_service.repository.AlertRepository;
import com.leetjourney.kafka.event.AlertingEvent;
import jakarta.mail.internet.MimeMessage;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: send an AlertingEvent on Kafka, verify alert-service consumes
 * it, sends an email via GreenMail SMTP, and persists an Alert row in MySQL.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class AlertConsumerIT {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("home_energy_tracker")
                    .withUsername("root")
                    .withPassword("password");

    @RegisterExtension
    static final GreenMailExtension GREENMAIL = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        r.add("spring.mail.host", () -> ServerSetupTest.SMTP.getBindAddress());
        r.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
    }

    @Autowired
    KafkaTemplate<String, AlertingEvent> kafkaTemplate;

    @Autowired
    AlertRepository alertRepository;

    @Test
    void alertingEvent_sendsEmailAndPersistsAlert() {
        AlertingEvent event = AlertingEvent.builder()
                .userId(1234L)
                .message("Energy threshold exceeded")
                .threshold(100.0)
                .energyConsumed(500.0)
                .email("test@example.com")
                .build();

        kafkaTemplate.send("energy-alerts", event);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    MimeMessage[] received = GREENMAIL.getReceivedMessages();
                    assertThat(received).hasSizeGreaterThanOrEqualTo(1);
                    MimeMessage msg = received[0];
                    assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("test@example.com");
                    assertThat(msg.getSubject()).contains("1234");
                    assertThat(GREENMAIL.getReceivedMessages()[0].getContent().toString())
                            .contains("Energy threshold exceeded");
                });

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(alertRepository.findAll())
                                .anyMatch(a -> a.getUserId() == 1234L && a.isSent()));
    }
}
