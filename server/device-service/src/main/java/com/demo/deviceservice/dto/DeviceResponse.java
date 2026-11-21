package com.demo.deviceservice.dto;

import com.demo.deviceservice.entity.Device;
import com.demo.deviceservice.entity.DeviceType;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String name,
        DeviceType type,
        String serialNumber,
        UUID userId,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeviceResponse from(Device d) {
        return new DeviceResponse(
                d.getId(),
                d.getName(),
                d.getType(),
                d.getSerialNumber(),
                d.getUserId(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
