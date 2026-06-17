package com.demo.insightservice.service;

import com.demo.common.grpc.device.DeviceInfo;
import com.demo.common.grpc.device.DeviceServiceGrpc;
import com.demo.common.grpc.device.GetUserDevicesRequest;
import com.demo.common.grpc.device.GetUserDevicesResponse;
import com.demo.insightservice.entity.BreachedUser;
import com.demo.insightservice.repository.BreachedUserRepository;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class InsightScheduler {

    private static final Duration RETENTION = Duration.ofDays(3);
    private static final long GRPC_TIMEOUT_SECONDS = 30;

    private final BreachedUserRepository breachedUserRepository;
    private final InsightEmailService insightEmailService;
    private final InfluxDBClient influxDBClient;
    private final ChatClient chatClient;
    private final DeviceServiceGrpc.DeviceServiceFutureStub deviceServiceFutureStub;

    @Value("${influxDB.bucket}")
    private String influxDBBucket;
    @Value("${influxDB.org}")
    private String influxDBOrg;

    @Scheduled(cron = "${insight.cron.process:0 0 8 * * *}")
    public void processBreaches() {
        List<BreachedUser> unsent = breachedUserRepository.findUnsent();
        if (unsent.isEmpty()) {
            log.debug("No unsent breaches");
            return;
        }
        Map<UUID, String> userEmail = unsent.stream()
                .collect(Collectors.toMap(BreachedUser::getUserId, BreachedUser::getEmail, (a, b) -> a));

        Map<UUID, List<DeviceInfo>> resolved = fanOutGetUserDevices(userEmail.keySet());

        for (Map.Entry<UUID, String> entry : userEmail.entrySet()) {
            UUID userId = entry.getKey();
            String email = entry.getValue();
            List<DeviceInfo> devices = resolved.get(userId);
            if (devices == null || devices.isEmpty()) {
                log.warn("No devices resolved for user {}, skipping", userId);
                continue;
            }
            try {
                Map<String, Double> usageByType = queryLastThreeDaysByType(devices);
                String insight = generateInsight(usageByType);
                if (insightEmailService.sendInsight(email, insight)) {
                    markUserSent(userId);
                }
            } catch (Exception e) {
                log.error("Failed to generate/send insight for user {}", userId, e);
            }
        }
    }

    @Scheduled(cron = "${insight.cron.cleanup:0 0 3 * * *}")
    @Transactional
    public void cleanupOldRows() {
        Instant cutoff = Instant.now().minus(RETENTION);
        int deleted = breachedUserRepository.deleteOlderThan(cutoff);
        if (deleted > 0) log.info("Deleted {} breach rows older than {}", deleted, cutoff);
    }

    @Transactional
    void markUserSent(UUID userId) {
        breachedUserRepository.markUserSent(userId, Instant.now());
    }

    private Map<UUID, List<DeviceInfo>> fanOutGetUserDevices(Iterable<UUID> userIds) {
        Map<UUID, List<DeviceInfo>> result = new HashMap<>();
        CountDownLatch latch = new CountDownLatch((int) java.util.stream.StreamSupport
                .stream(userIds.spliterator(), false).count());

        for (UUID userId : userIds) {
            ListenableFuture<GetUserDevicesResponse> future = deviceServiceFutureStub
                    .withDeadlineAfter(GRPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getUserDevices(GetUserDevicesRequest.newBuilder().setUserId(userId.toString()).build());

            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(GetUserDevicesResponse response) {
                    synchronized (result) {
                        result.put(userId, response.getDevicesList());
                    }
                    latch.countDown();
                }

                @Override
                public void onFailure(Throwable t) {
                    log.warn("getUserDevices failed for user {}: {}", userId, t.getMessage());
                    latch.countDown();
                }
            }, MoreExecutors.directExecutor());
        }

        try {
            if (!latch.await(GRPC_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS)) {
                log.warn("fanOutGetUserDevices timed out; got {} of expected results", result.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    private Map<String, Double> queryLastThreeDaysByType(List<DeviceInfo> devices) {
        Instant now = Instant.now();
        Instant threeDaysAgo = now.minus(RETENTION);

        Map<String, String> deviceIdToType = devices.stream()
                .collect(Collectors.toMap(DeviceInfo::getDeviceId, DeviceInfo::getDeviceType, (a, b) -> a));
        String deviceFilter = deviceIdToType.keySet().stream()
                .map(id -> "r[\"deviceId\"] == \"" + id + "\"")
                .collect(Collectors.joining(" or "));
        if (deviceFilter.isEmpty()) return Map.of();

        String query = String.format("""
                from(bucket: "%s")
                  |> range(start: time(v: "%s"), stop: time(v: "%s"))
                  |> filter(fn: (r) => r["_measurement"] == "energy-usage")
                  |> filter(fn: (r) => r["_field"] == "energyConsumed")
                  |> filter(fn: (r) => %s)
                  |> group(columns: ["deviceId"])
                  |> sum(column: "_value")
                """, influxDBBucket, threeDaysAgo, now, deviceFilter);

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(query, influxDBOrg);

        Map<String, Double> byType = new LinkedHashMap<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                String deviceId = (String) record.getValueByKey("deviceId");
                Object v = record.getValueByKey("_value");
                double kwh = v instanceof Number n ? n.doubleValue() : 0.0;
                String type = deviceIdToType.getOrDefault(deviceId, "UNKNOWN");
                byType.merge(type, kwh, Double::sum);
            }
        }
        return byType;
    }

    private String generateInsight(Map<String, Double> usageByType) {
        String usage = usageByType.entrySet().stream()
                .map(e -> "- %s: %.2f kWh".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));
        String prompt = """
                The user breached their alert threshold recently.
                Last 3 days of total energy consumption, grouped by device type:
                %s

                Write a short, friendly insight (under 120 words) summarising which device types
                drove their usage and offering one concrete tip to reduce consumption.
                """.formatted(usage.isEmpty() ? "(no usage data available)" : usage);

        return chatClient.prompt().user(prompt).call().content();
    }
}
