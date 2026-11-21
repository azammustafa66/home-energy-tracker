package com.demo.ingestionservice.simulation;

import com.demo.ingestionservice.dto.EnergyUsageDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "simulation.mode", havingValue = "parallel")
public class ParallelDataSimulator implements CommandLineRunner {

    private final ClientCredentialsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final List<UUID> deviceIds = new ArrayList<>();

    @Value("${simulation.seed-file:scripts/seed-output.json}")
    private String seedFile;
    @Value("${simulation.ingestion-url:http://localhost:8083/api/v1/ingestion}")
    private String ingestionUrl;
    @Value("${simulation.max-energy-consumed:50.0}")
    private double maxEnergyConsumed;

    private ExecutorService executorService;
    @Value("${simulation.threads:5}")
    private int threads;
    @Value("${simulation.requests-per-interval}")
    private int requestsPerInterval;

    @PostConstruct
    void init() {
        this.executorService = Executors.newFixedThreadPool(threads);
    }

    @Override
    public void run(String @NonNull ... args) throws Exception {
        log.info("ParallelDataSimulator started");
        Path path = Paths.get(seedFile);
        if (!Files.exists(path)) {
            log.warn("Seed file {} not found (cwd: {}). Simulator will stay idle.",
                    path.toAbsolutePath(), Path.of("").toAbsolutePath());
            return;
        }
        JsonNode root = objectMapper.readTree(path.toFile());
        JsonNode devices = root.get("devices");
        if (!devices.isArray() || devices.isEmpty()) {
            log.warn("No 'devices' array in {}. Simulator will stay idle.", path.toAbsolutePath());
            return;
        }
        for (JsonNode device : devices) {
            String id = device.get("id").asText(null);
            if (id != null) deviceIds.add(UUID.fromString(id));
        }
        log.info("ParallelDataSimulator loaded {} device IDs from {}", deviceIds.size(), path.toAbsolutePath());
    }

    @Scheduled(fixedRateString = "${simulation.interval-ms}")
    public void sendMockData() {
        if (deviceIds.isEmpty()) {
            return;
        }
        int batchSize = requestsPerInterval / threads;
        int remainder = requestsPerInterval % threads;

        for (int i = 0; i < threads; i++) {
            int requestsForThread = batchSize + (i < remainder ? 1 : 0);
            executorService.submit(() -> {
                for (int j = 0; j < requestsForThread; j++) {
                    UUID deviceId = deviceIds.get(ThreadLocalRandom.current().nextInt(deviceIds.size()));
                    EnergyUsageDTO dto = new EnergyUsageDTO(
                            deviceId,
                            Math.round(ThreadLocalRandom.current().nextDouble() * maxEnergyConsumed * 100.0) / 100.0,
                            Instant.now()
                    );
                    try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setBearerAuth(tokenProvider.getToken());
                        HttpEntity<EnergyUsageDTO> request = new HttpEntity<>(dto, headers);
                        restTemplate.postForEntity(ingestionUrl, request, Void.class);
                    } catch (Exception ex) {
                        log.warn("Failed POST to {} for device {}: {}", ingestionUrl, deviceId, ex.getMessage());
                    }
                }
            });
        }
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdown();
        log.info("ParallelDataSimulator stopped");
    }
}
