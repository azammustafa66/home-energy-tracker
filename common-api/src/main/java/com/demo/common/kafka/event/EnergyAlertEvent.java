package com.demo.common.kafka.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record EnergyAlertEvent(
        UUID userId,
        String email,
        double consumption,
        double threshold,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant windowStart,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant windowEnd) {
}
