package com.demo.ingestionservice.simulation;

import com.demo.ingestionservice.dto.EnergyUsageDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "simulation.mode", havingValue = "continuous")
public class ContinuousDataSimulator implements CommandLineRunner {

    private final ClientCredentialsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();
    private final List<UUID> deviceIds = new ArrayList<>();

    @Value("${simulation.requests-per-interval:10000}")
    private int requestsPerInterval;

    @Value("${simulation.seed-file:scripts/seed-output.json}")
    private String seedFile;

    @Value("${simulation.ingestion-url:http://localhost:8083/api/v1/ingestion}")
    private String ingestionUrl;

    @Value("${simulation.max-energy-consumed:50.0}")
    private double maxEnergyConsumed;

    @Override
    public void run(String @NonNull ... args) throws Exception {
        Path path = Path.of(seedFile);
        if (!Files.exists(path)) {
            log.warn("Seed file {} not found (cwd: {}). Simulator will stay idle.",
                    path.toAbsolutePath(), Path.of("").toAbsolutePath());
            return;
        }
        JsonNode root = objectMapper.readTree(Files.readString(path));
        JsonNode devices = root.path("devices");
        if (!devices.isArray() || devices.isEmpty()) {
            log.warn("No 'devices' array in {}. Simulator will stay idle.", path.toAbsolutePath());
            return;
        }
        for (JsonNode d : devices) {
            String id = d.path("id").asText(null);
            if (id != null) {
                deviceIds.add(UUID.fromString(id));
            }
        }
        log.info("ContinuousDataSimulator loaded {} device IDs from {}", deviceIds.size(), path.toAbsolutePath());
    }

    @Scheduled(fixedRateString = "${simulation.interval-ms:100}")
    public void sendMockData() {
        if (deviceIds.isEmpty()) {
            return;
        }
        for (int i = 0; i < requestsPerInterval; i++) {
            UUID deviceId = deviceIds.get(random.nextInt(deviceIds.size()));
            EnergyUsageDTO dto = new EnergyUsageDTO(
                    deviceId,
                    Math.round(random.nextDouble() * maxEnergyConsumed * 100.0) / 100.0,
                    Instant.now()
            );
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(tokenProvider.getToken());
                HttpEntity<EnergyUsageDTO> request = new HttpEntity<>(dto, headers);
                restTemplate.postForEntity(ingestionUrl, request, Void.class);
                log.info("Sent mock data: {}", dto);
            } catch (Exception ex) {
                log.warn("Failed POST to {} for device {}: {}", ingestionUrl, deviceId, ex.getMessage());
            }
        }
    }
}
