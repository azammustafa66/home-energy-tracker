package com.leetjouney.device_service.service;

import com.leetjouney.device_service.dto.DeviceDto;
import com.leetjouney.device_service.entity.Device;
import com.leetjouney.device_service.exception.DeviceNotFoundException;
import com.leetjouney.device_service.model.DeviceType;
import com.leetjouney.device_service.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    private Device device(long id, long userId) {
        return Device.builder()
                .id(id).name("Speaker").type(DeviceType.SPEAKER)
                .location("living room").userId(userId).build();
    }

    @Test
    void getDeviceById_whenPresent_returnsDto() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device(1L, 99L)));

        DeviceDto dto = deviceService.getDeviceById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Speaker");
        assertThat(dto.getType()).isEqualTo(DeviceType.SPEAKER);
        assertThat(dto.getUserId()).isEqualTo(99L);
    }

    @Test
    void getDeviceById_whenAbsent_throwsDeviceNotFoundException() {
        when(deviceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.getDeviceById(404L))
                .isInstanceOf(DeviceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void createDevice_persistsDeviceAndReturnsSavedDto() {
        DeviceDto input = DeviceDto.builder()
                .name("Bulb").type(DeviceType.LIGHT).location("hall").userId(5L).build();

        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device d = invocation.getArgument(0);
            d.setId(77L);
            return d;
        });

        DeviceDto out = deviceService.createDevice(input);

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Bulb");
        assertThat(saved.getType()).isEqualTo(DeviceType.LIGHT);
        assertThat(out.getId()).isEqualTo(77L);
    }

    @Test
    void updateDevice_whenPresent_savesAndReturnsUpdatedDto() {
        Device existing = device(10L, 1L);
        when(deviceRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceDto input = DeviceDto.builder()
                .name("Renamed").type(DeviceType.CAMERA)
                .location("garage").userId(1L).build();

        DeviceDto out = deviceService.updateDevice(10L, input);

        assertThat(out.getName()).isEqualTo("Renamed");
        assertThat(out.getType()).isEqualTo(DeviceType.CAMERA);
        assertThat(out.getLocation()).isEqualTo("garage");
        verify(deviceRepository).save(existing);
    }

    @Test
    void updateDevice_whenAbsent_throwsDeviceNotFoundException() {
        when(deviceRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.updateDevice(123L,
                DeviceDto.builder().name("x").build()))
                .isInstanceOf(DeviceNotFoundException.class);
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void deleteDevice_whenPresent_callsRepositoryDelete() {
        when(deviceRepository.existsById(11L)).thenReturn(true);

        deviceService.deleteDevice(11L);

        verify(deviceRepository).deleteById(11L);
    }

    @Test
    void deleteDevice_whenAbsent_throwsDeviceNotFoundException() {
        when(deviceRepository.existsById(11L)).thenReturn(false);

        assertThatThrownBy(() -> deviceService.deleteDevice(11L))
                .isInstanceOf(DeviceNotFoundException.class);
        verify(deviceRepository, never()).deleteById(any());
    }

    @Test
    void getAllDevicesByUserId_mapsAllEntitiesToDtos() {
        when(deviceRepository.findAllByUserId(5L)).thenReturn(List.of(
                device(1L, 5L), device(2L, 5L)));

        List<DeviceDto> dtos = deviceService.getAllDevicesByUserId(5L);

        assertThat(dtos).hasSize(2);
        assertThat(dtos).extracting(DeviceDto::getId).containsExactly(1L, 2L);
        assertThat(dtos).allSatisfy(d -> assertThat(d.getUserId()).isEqualTo(5L));
    }
}
