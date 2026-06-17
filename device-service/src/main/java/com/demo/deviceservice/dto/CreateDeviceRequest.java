package com.demo.deviceservice.dto;

import com.demo.deviceservice.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDeviceRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150)
        String name,

        @NotNull(message = "type is required")
        DeviceType type,

        @NotBlank(message = "serialNumber is required")
        @Size(max = 100)
        String serialNumber,

        @NotNull(message = "userId is required")
        UUID userId
) {}
