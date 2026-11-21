package com.demo.userservice.dto;

import com.demo.userservice.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        boolean alerting,
        Double energyAlertThreshold,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getPhone(),
                u.isAlerting(),
                u.getEnergyAlertThreshold(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
