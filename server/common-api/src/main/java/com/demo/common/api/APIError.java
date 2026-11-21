package com.demo.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIError {

    private int status;
    private String code;
    private String message;
    private Map<String, String> fieldErrors;
}
