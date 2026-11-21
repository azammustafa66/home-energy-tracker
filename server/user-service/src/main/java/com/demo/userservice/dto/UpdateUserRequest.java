package com.demo.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @Email(message = "email must be valid")
        @Size(max = 255)
        String email,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Pattern(regexp = "^[+0-9\\-\\s()]{7,30}$", message = "phone must be 7-30 chars of digits/+/-/space/()")
        String phone,

        Boolean alerting,

        @PositiveOrZero(message = "energyAlertThreshold must be >= 0")
        Double energyAlertThreshold
) {}
