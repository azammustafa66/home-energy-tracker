package com.demo.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIResponse<T> {

    private boolean success;
    private T data;
    private APIError error;
    private Instant timestamp;

    public static <T> APIResponse<T> ok(T data) {
        return APIResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> APIResponse<T> ok() {
        return APIResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> APIResponse<T> fail(APIError error) {
        return APIResponse.<T>builder()
                .success(false)
                .error(error)
                .timestamp(Instant.now())
                .build();
    }
}
