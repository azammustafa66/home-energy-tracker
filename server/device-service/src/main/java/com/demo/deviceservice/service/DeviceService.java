package com.demo.deviceservice.service;

import com.demo.deviceservice.dto.CreateDeviceRequest;
import com.demo.deviceservice.dto.DeviceResponse;
import com.demo.deviceservice.dto.UpdateDeviceRequest;
import com.demo.deviceservice.entity.Device;
import com.demo.deviceservice.exception.DeviceNotFoundException;
import com.demo.deviceservice.exception.SerialNumberAlreadyUsedException;
import com.demo.deviceservice.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceResponse create(CreateDeviceRequest req) {
        if (deviceRepository.existsBySerialNumber(req.serialNumber())) {
            throw new SerialNumberAlreadyUsedException(req.serialNumber());
        }
        Device saved = deviceRepository.save(Device.builder()
                .name(req.name())
                .type(req.type())
                .serialNumber(req.serialNumber())
                .userId(req.userId())
                .build());
        return DeviceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(UUID id) {
        return DeviceResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list(UUID userId) {
        List<Device> devices = (userId == null)
                ? deviceRepository.findAll()
                : deviceRepository.findByUserId(userId);
        return devices.stream().map(DeviceResponse::from).toList();
    }

    public DeviceResponse update(UUID id, UpdateDeviceRequest req) {
        Device device = findOrThrow(id);
        if (req.serialNumber() != null && !req.serialNumber().equals(device.getSerialNumber())) {
            if (deviceRepository.existsBySerialNumber(req.serialNumber())) {
                throw new SerialNumberAlreadyUsedException(req.serialNumber());
            }
            device.setSerialNumber(req.serialNumber());
        }
        if (req.name() != null) device.setName(req.name());
        if (req.type() != null) device.setType(req.type());
        return DeviceResponse.from(device);
    }

    public void delete(UUID id) {
        Device device = findOrThrow(id);
        deviceRepository.delete(device);
    }

    private Device findOrThrow(UUID id) {
        return deviceRepository.findById(id).orElseThrow(() -> new DeviceNotFoundException(id));
    }
}
