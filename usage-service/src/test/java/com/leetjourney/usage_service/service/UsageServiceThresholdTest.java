package com.leetjourney.usage_service.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.leetjourney.kafka.event.AlertingEvent;
import com.leetjourney.usage_service.client.DeviceClient;
import com.leetjourney.usage_service.client.UserClient;
import com.leetjourney.usage_service.dto.DeviceDto;
import com.leetjourney.usage_service.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the {@code aggregateDeviceEnergyUsage} scheduled method by feeding
 * synthetic Flux records and verifies that the {@code energy-alerts} topic
 * receives messages only when total consumption strictly exceeds the user's
 * threshold (matches code: {@code totalConsumption > threshold}).
 */
@ExtendWith(MockitoExtension.class)
class UsageServiceThresholdTest {

    @Mock private InfluxDBClient influxDBClient;
    @Mock private QueryApi queryApi;
    @Mock private DeviceClient deviceClient;
    @Mock private UserClient userClient;
    @Mock private KafkaTemplate<String, AlertingEvent> kafkaTemplate;

    private UsageService usageService;

    @BeforeEach
    void setUp() {
        usageService = new UsageService(influxDBClient, deviceClient, userClient, kafkaTemplate);
        ReflectionTestUtils.setField(usageService, "influxBucket", "test-bucket");
        ReflectionTestUtils.setField(usageService, "influxOrg", "test-org");
    }

    private FluxRecord fluxRecord(String deviceId, double value) {
        FluxRecord record = new FluxRecord(0);
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("deviceId", deviceId);
        values.put("_value", value);
        ReflectionTestUtils.setField(record, "values", values);
        return record;
    }

    private FluxTable fluxTableOf(FluxRecord... records) {
        FluxTable table = new FluxTable();
        table.getRecords().addAll(java.util.Arrays.asList(records));
        return table;
    }

    private DeviceDto device(long id, long userId) {
        return DeviceDto.builder()
                .id(id).name("d" + id).type("LIGHT")
                .location("loc").userId(userId).energyConsumed(0.0)
                .build();
    }

    private UserDto user(long id, double threshold, boolean alerting) {
        return UserDto.builder()
                .id(id).name("u").surname("s").email("u" + id + "@x.com")
                .address("addr").alerting(alerting).energyAlertingThreshold(threshold)
                .build();
    }

    @Test
    void aggregateDeviceEnergyUsage_whenTotalExceedsThreshold_emitsAlert() {
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        FluxRecord r1 = fluxRecord("100", 60.0);
        FluxRecord r2 = fluxRecord("101", 50.0);
        when(queryApi.query(anyString(), eq("test-org")))
                .thenReturn(List.of(fluxTableOf(r1, r2)));

        when(deviceClient.getDeviceById(100L)).thenReturn(device(100L, 7L));
        when(deviceClient.getDeviceById(101L)).thenReturn(device(101L, 7L));
        when(userClient.getUserById(7L)).thenReturn(user(7L, 100.0, true));

        usageService.aggregateDeviceEnergyUsage();

        ArgumentCaptor<AlertingEvent> captor = ArgumentCaptor.forClass(AlertingEvent.class);
        verify(kafkaTemplate).send(eq("energy-alerts"), captor.capture());
        AlertingEvent ev = captor.getValue();
        assertThat(ev.userId()).isEqualTo(7L);
        assertThat(ev.threshold()).isEqualTo(100.0);
        assertThat(ev.energyConsumed()).isEqualTo(110.0);
        assertThat(ev.email()).isEqualTo("u7@x.com");
        assertThat(ev.message()).contains("threshold exceeded");
    }

    @Test
    void aggregateDeviceEnergyUsage_whenTotalEqualsThreshold_doesNotEmitAlert() {
        // boundary: strictly greater than required to alert; equality must NOT alert
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        FluxRecord r1 = fluxRecord("200", 100.0);
        when(queryApi.query(anyString(), eq("test-org")))
                .thenReturn(List.of(fluxTableOf(r1)));

        when(deviceClient.getDeviceById(200L)).thenReturn(device(200L, 8L));
        when(userClient.getUserById(8L)).thenReturn(user(8L, 100.0, true));

        usageService.aggregateDeviceEnergyUsage();

        verify(kafkaTemplate, never()).send(anyString(), any(AlertingEvent.class));
    }

    @Test
    void aggregateDeviceEnergyUsage_whenTotalBelowThreshold_doesNotEmitAlert() {
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        FluxRecord r1 = fluxRecord("300", 50.0);
        when(queryApi.query(anyString(), eq("test-org")))
                .thenReturn(List.of(fluxTableOf(r1)));

        when(deviceClient.getDeviceById(300L)).thenReturn(device(300L, 9L));
        when(userClient.getUserById(9L)).thenReturn(user(9L, 100.0, true));

        usageService.aggregateDeviceEnergyUsage();

        verify(kafkaTemplate, never()).send(anyString(), any(AlertingEvent.class));
    }

    @Test
    void aggregateDeviceEnergyUsage_whenUserAlertingDisabled_doesNotEmitAlert() {
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        FluxRecord r1 = fluxRecord("400", 1000.0);
        when(queryApi.query(anyString(), eq("test-org")))
                .thenReturn(List.of(fluxTableOf(r1)));

        when(deviceClient.getDeviceById(400L)).thenReturn(device(400L, 10L));
        when(userClient.getUserById(10L)).thenReturn(user(10L, 5.0, false));

        usageService.aggregateDeviceEnergyUsage();

        verify(kafkaTemplate, never()).send(anyString(), any(AlertingEvent.class));
    }

    @Test
    void aggregateDeviceEnergyUsage_whenDeviceLookupFails_skipsThatDevice() {
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        FluxRecord ok = fluxRecord("500", 60.0);
        FluxRecord broken = fluxRecord("501", 1000.0);
        when(queryApi.query(anyString(), eq("test-org")))
                .thenReturn(List.of(fluxTableOf(ok, broken)));

        when(deviceClient.getDeviceById(500L)).thenReturn(device(500L, 11L));
        when(deviceClient.getDeviceById(501L)).thenThrow(new RuntimeException("device service down"));
        when(userClient.getUserById(11L)).thenReturn(user(11L, 10.0, true));

        usageService.aggregateDeviceEnergyUsage();

        ArgumentCaptor<AlertingEvent> captor = ArgumentCaptor.forClass(AlertingEvent.class);
        verify(kafkaTemplate).send(eq("energy-alerts"), captor.capture());
        // only the OK device contributed
        assertThat(captor.getValue().energyConsumed()).isEqualTo(60.0);
    }

    @Test
    void aggregateDeviceEnergyUsage_whenDeviceReturnsNull_isSkipped() {
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        FluxRecord r = fluxRecord("600", 200.0);
        when(queryApi.query(anyString(), eq("test-org")))
                .thenReturn(List.of(fluxTableOf(r)));

        when(deviceClient.getDeviceById(600L)).thenReturn(null);

        usageService.aggregateDeviceEnergyUsage();

        verify(kafkaTemplate, never()).send(anyString(), any(AlertingEvent.class));
    }
}
