package com.demo.deviceservice.exception;

import com.demo.common.api.APIError;
import com.demo.common.api.APIResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<APIResponse<Void>> handleNotFound(DeviceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(SerialNumberAlreadyUsedException.class)
    public ResponseEntity<APIResponse<Void>> handleConflict(SerialNumberAlreadyUsedException ex) {
        return build(HttpStatus.CONFLICT, "SERIAL_NUMBER_ALREADY_USED", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "validation failed", fieldErrors);
    }

    private ResponseEntity<APIResponse<Void>> build(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        APIError error = APIError.builder()
                .status(status.value())
                .code(code)
                .message(message)
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(status).body(APIResponse.fail(error));
    }
}
