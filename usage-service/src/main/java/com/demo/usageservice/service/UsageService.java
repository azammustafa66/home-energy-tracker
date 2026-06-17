package com.demo.usageservice.service;

import com.demo.common.grpc.device.*;
import com.demo.common.grpc.energyThreshold.EnergyThresholdServiceGrpc;
import com.demo.common.grpc.energyThreshold.GetUserThresholdsRequest;
import com.demo.common.grpc.energyThreshold.GetUserThresholdsResponse;
import com.demo.common.grpc.energyThreshold.UserThresholdInfo;
import com.demo.common.kafka.event.EnergyAlertEvent;
import com.demo.usageservice.records.UserThreshold;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.demo.common.kafka.event.EnergyUsageEvent;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsageService {

    private final InfluxDBClient influxDBClient;
    private final WriteApi writeApi;
    private final DeviceServiceGrpc.DeviceServiceBlockingStub deviceServiceStub;
    private final EnergyThresholdServiceGrpc.EnergyThresholdServiceBlockingStub energyThresholdServiceStub;
    private final KafkaTemplate<String, EnergyAlertEvent> kafkaTemplate;

    private final ConcurrentHashMap<UUID, UUID> deviceUserCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UserThreshold> thresholdCache = new ConcurrentHashMap<>();

    @Value("${influxDB.bucket}")
    private String influxDBBucket;
    @Value("${influxDB.org}")
    private String influxDBOrg;

    @KafkaListener(topics = "energy-usage", groupId = "usage-service")
    public void consume(EnergyUsageEvent event) {
        log.info("Consuming energy usage event {}", event);
        Point point = Point.measurement("energy-usage").addTag("deviceId", String.valueOf(event.deviceId())).addField("energyConsumed", event.energyConsumed()).time(event.timestamp(), WritePrecision.MS);
        writeApi.writePoint(influxDBBucket, influxDBOrg, point);
    }

    @Scheduled(cron = "*/10 * * * * *")
    public void aggregateDeviceEnergyUsage() {
        try {
            doAggregate();
        } catch (Exception e) {
            log.error("aggregateDeviceEnergyUsage tick failed", e);
        }
    }

    private void doAggregate() {
        final Instant now = Instant.now();
        final Instant oneHourAgo = now.minusSeconds(3600);
        final String query = String.format("""
                from(bucket: "%s")
                |> range(start: time(v: "%s"), stop: time(v: "%s"))
                |> filter(fn: (r) => r["_measurement"] == "energy-usage")
                |> filter(fn: (r) => r["_field"] == "energyConsumed")
                |> group(columns: ["deviceId"])
                |> sum(column: "_value")
                """, influxDBBucket, oneHourAgo.toString(), now
        );
        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(query, influxDBOrg);

        Map<UUID, Double> deviceConsumption = new HashMap<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                String deviceId = (String) record.getValueByKey("deviceId");
                Object value = record.getValueByKey("_value");
                double energy = value instanceof Number n ? n.doubleValue() : 0.0;
                deviceConsumption.merge(UUID.fromString(deviceId), energy, Double::sum);
            }
        }
        if (deviceConsumption.isEmpty()) return;

        Map<UUID, UUID> deviceToUser = resolveUserIds(deviceConsumption.keySet());

        Map<UUID, Double> userConsumption = new HashMap<>();
        for (Map.Entry<UUID, Double> entry : deviceConsumption.entrySet()) {
            UUID userId = deviceToUser.get(entry.getKey());
            if (userId == null) {
                log.warn("Device {} has no userId, skipping", entry.getKey());
                continue;
            }
            userConsumption.merge(userId, entry.getValue(), Double::sum);
        }

        Map<UUID, UserThreshold> thresholds = resolveThresholds(userConsumption.keySet());
        log.debug("User consumption: {}", userConsumption);
        log.debug("User thresholds: {}", thresholds);


        for (Map.Entry<UUID, Double> entry : userConsumption.entrySet()) {
            UUID userId = entry.getKey();
            double consumption = entry.getValue();

            UserThreshold userThreshold = thresholds.get(userId);
            if (userThreshold == null || userThreshold.alertThreshold() == null) continue;

            if (consumption > userThreshold.alertThreshold()) {
                EnergyAlertEvent event = EnergyAlertEvent.builder()
                        .userId(userId)
                        .email(userThreshold.email())
                        .consumption(consumption)
                        .threshold(userThreshold.alertThreshold())
                        .windowStart(oneHourAgo)
                        .windowEnd(now)
                        .build();

                kafkaTemplate.send("threshold-breaches", userId.toString(), event).whenComplete((r, e) -> {
                    if (e != null) {
                        log.error("Error while sending user threshold message", e);
                    } else {
                        log.info("Published user-breach event for user {}", userId);
                    }
                });
            }
        }
    }

    private Map<UUID, UUID> resolveUserIds(Set<UUID> deviceIds) {
        Map<UUID, UUID> result = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (UUID deviceId : deviceIds) {
            UUID cached = deviceUserCache.get(deviceId);
            if (cached != null) result.put(deviceId, cached);
            else missing.add(deviceId.toString());
        }
        if (missing.isEmpty()) return result;

        try {
            GetDeviceUsersResponse response = deviceServiceStub.getDeviceUsers(
                    GetDeviceUsersRequest.newBuilder().addAllDeviceId(missing).build());
            for (Map.Entry<String, String> entry : response.getDeviceToUserMap().entrySet()) {
                UUID deviceId = UUID.fromString(entry.getKey());
                UUID userId = UUID.fromString(entry.getValue());
                deviceUserCache.put(deviceId, userId);
                result.put(deviceId, userId);
            }
        } catch (StatusRuntimeException e) {
            log.warn("Batch getDeviceUsers failed for {} devices: {}", missing.size(), e.getStatus());
        }

        return result;
    }

    private Map<UUID, UserThreshold> resolveThresholds(Set<UUID> userIds) {
        Map<UUID, UserThreshold> result = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (UUID userId : userIds) {
            UserThreshold cached = thresholdCache.get(userId);
            if (cached != null) result.put(userId, cached);
            else missing.add(userId.toString());
        }
        if (missing.isEmpty()) return result;

        try {
            GetUserThresholdsResponse response = energyThresholdServiceStub.getUserEnergyThresholds(
                    GetUserThresholdsRequest.newBuilder().addAllUserId(missing).build());
            for (Map.Entry<String, UserThresholdInfo> entry : response.getThresholdsMap().entrySet()) {
                UUID userId = UUID.fromString(entry.getKey());
                UserThresholdInfo info = entry.getValue();
                Double threshold = info.hasAlertThreshold() ? info.getAlertThreshold() : null;
                UserThreshold ut = new UserThreshold(threshold, info.getEmail());
                thresholdCache.put(userId, ut);
                result.put(userId, ut);
            }
        } catch (StatusRuntimeException e) {
            log.warn("Batch getUserEnergyThresholds failed for {} users: {}", missing.size(), e.getStatus());
        }
        return result;
    }
}
