package com.leetjourney.usage_service.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.write.Point;
import com.leetjourney.kafka.event.AlertingEvent;
import com.leetjourney.kafka.event.EnergyUsageEvent;
import com.leetjourney.usage_service.client.DeviceClient;
import com.leetjourney.usage_service.client.UserClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private WriteApiBlocking writeApiBlocking;

    @Mock
    private DeviceClient deviceClient;

    @Mock
    private UserClient userClient;

    @Mock
    private KafkaTemplate<String, AlertingEvent> kafkaTemplate;

    private UsageService usageService;

    @BeforeEach
    void setUp() {
        usageService = new UsageService(influxDBClient, deviceClient, userClient, kafkaTemplate);
        ReflectionTestUtils.setField(usageService, "influxBucket", "test-bucket");
        ReflectionTestUtils.setField(usageService, "influxOrg", "test-org");
    }

    @Test
    void energyUsageEvent_writesPointToInflux() {
        when(influxDBClient.getWriteApiBlocking()).thenReturn(writeApiBlocking);

        EnergyUsageEvent event = EnergyUsageEvent.builder()
                .deviceId(13L)
                .energyConsumed(2.5)
                .timestamp(Instant.parse("2024-06-12T10:00:00Z"))
                .build();

        usageService.energyUsageEvent(event);

        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);
        verify(writeApiBlocking).writePoint(eq("test-bucket"), eq("test-org"), pointCaptor.capture());

        Point captured = pointCaptor.getValue();
        // toLineProtocol should contain the measurement, tag, and field
        String line = captured.toLineProtocol();
        assertThat(line).contains("energy_usage");
        assertThat(line).contains("deviceId=13");
        assertThat(line).contains("energyConsumed=2.5");
    }

    @Test
    void energyUsageEvent_handlesZeroEnergyValueWithoutError() {
        when(influxDBClient.getWriteApiBlocking()).thenReturn(writeApiBlocking);

        EnergyUsageEvent event = EnergyUsageEvent.builder()
                .deviceId(1L)
                .energyConsumed(0.0)
                .timestamp(Instant.now())
                .build();

        usageService.energyUsageEvent(event);

        verify(writeApiBlocking).writePoint(eq("test-bucket"), eq("test-org"), any(Point.class));
    }

    @Test
    void aggregateDeviceEnergyUsage_withEmptyInflux_doesNotPublishAlerts() {
        com.influxdb.client.QueryApi queryApi = org.mockito.Mockito.mock(com.influxdb.client.QueryApi.class);
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), eq("test-org"))).thenReturn(java.util.List.of());

        usageService.aggregateDeviceEnergyUsage();

        verify(kafkaTemplate, never()).send(anyString(), any(AlertingEvent.class));
    }

    @Test
    void getXDaysUsageForUser_whenUserHasNoDevices_returnsUsageWithNullDevices() {
        when(deviceClient.getAllDevicesForUser(99L)).thenReturn(java.util.List.of());

        com.leetjourney.usage_service.dto.UsageDto result =
                usageService.getXDaysUsageForUser(99L, 3);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(99L);
        assertThat(result.devices()).isNull();
        // Influx is never queried since there are no devices
        verify(influxDBClient, never()).getQueryApi();
    }

    @Test
    void getXDaysUsageForUser_whenInfluxQueryFails_returnsUsageWithNullDevices() {
        com.leetjourney.usage_service.dto.DeviceDto deviceDto =
                com.leetjourney.usage_service.dto.DeviceDto.builder()
                        .id(1L).name("d1").type("LIGHT")
                        .location("kitchen").userId(99L).energyConsumed(0.0)
                        .build();
        when(deviceClient.getAllDevicesForUser(99L))
                .thenReturn(java.util.List.of(deviceDto));

        com.influxdb.client.QueryApi queryApi = org.mockito.Mockito.mock(com.influxdb.client.QueryApi.class);
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), anyString()))
                .thenThrow(new RuntimeException("influx down"));

        com.leetjourney.usage_service.dto.UsageDto result =
                usageService.getXDaysUsageForUser(99L, 3);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(99L);
        assertThat(result.devices()).isNull();
    }

    @Test
    void getXDaysUsageForUser_withDevicesAndEmptyInflux_returnsDevicesWithZeroEnergy() {
        com.leetjourney.usage_service.dto.DeviceDto d1 =
                com.leetjourney.usage_service.dto.DeviceDto.builder()
                        .id(1L).name("d1").type("LIGHT")
                        .location("kitchen").userId(99L).energyConsumed(0.0)
                        .build();
        com.leetjourney.usage_service.dto.DeviceDto d2 =
                com.leetjourney.usage_service.dto.DeviceDto.builder()
                        .id(2L).name("d2").type("CAMERA")
                        .location("hall").userId(99L).energyConsumed(0.0)
                        .build();
        when(deviceClient.getAllDevicesForUser(99L))
                .thenReturn(java.util.List.of(d1, d2));

        com.influxdb.client.QueryApi queryApi = org.mockito.Mockito.mock(com.influxdb.client.QueryApi.class);
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), eq("test-org"))).thenReturn(java.util.List.of());

        com.leetjourney.usage_service.dto.UsageDto result =
                usageService.getXDaysUsageForUser(99L, 7);

        assertThat(result.userId()).isEqualTo(99L);
        assertThat(result.devices()).hasSize(2);
        assertThat(result.devices()).allSatisfy(d ->
                assertThat(d.energyConsumed()).isEqualTo(0.0));
    }
}
