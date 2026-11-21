package com.demo.usageservice.model;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeviceEnergy {
    UUID deviceId;
    double energyConsumed;
    UUID userId;
}

