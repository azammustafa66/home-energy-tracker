package com.demo.deviceservice.dto;

import com.demo.deviceservice.entity.DeviceType;
import jakarta.validation.constraints.Size;

public record UpdateDeviceRequest(

        @Size(max = 150)
        String name,

        DeviceType type,

        @Size(max = 100)
        String serialNumber
) {}
