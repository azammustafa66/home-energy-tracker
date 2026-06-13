package com.leetjourney.usage_service;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Kafka + InfluxDB Testcontainers harness for usage-service ITs.
 */
@Testcontainers
public abstract class UsageServiceTestcontainers {

    static final String INFLUX_TOKEN = "my-token";
    static final String INFLUX_ORG = "leetjourney";
    static final String INFLUX_BUCKET = "usage-bucket";

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @SuppressWarnings("resource")
    static final GenericContainer<?> INFLUX =
            new GenericContainer<>(DockerImageName.parse("influxdb:2.7"))
                    .withExposedPorts(8086)
                    .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                    .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "admin")
                    .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "adminpass")
                    .withEnv("DOCKER_INFLUXDB_INIT_ORG", INFLUX_ORG)
                    .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", INFLUX_BUCKET)
                    .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", INFLUX_TOKEN)
                    .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    static {
        KAFKA.start();
        INFLUX.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("influx.url",
                () -> "http://" + INFLUX.getHost() + ":" + INFLUX.getMappedPort(8086));
        registry.add("influx.token", () -> INFLUX_TOKEN);
        registry.add("influx.org", () -> INFLUX_ORG);
        registry.add("influx.bucket", () -> INFLUX_BUCKET);
    }
}
