package com.demo.deviceservice.controller;

import com.demo.common.api.APIResponse;
import com.demo.deviceservice.dto.CreateDeviceRequest;
import com.demo.deviceservice.dto.DeviceResponse;
import com.demo.deviceservice.dto.UpdateDeviceRequest;
import com.demo.deviceservice.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<APIResponse<DeviceResponse>> create(@Valid @RequestBody CreateDeviceRequest req) {
        DeviceResponse created = deviceService.create(req);
        return ResponseEntity
                .created(URI.create("/api/v1/devices/" + created.id()))
                .body(APIResponse.ok(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<DeviceResponse> get(@PathVariable UUID id) {
        return APIResponse.ok(deviceService.get(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (#userId != null and #userId.toString() == authentication.token.subject)")
    public APIResponse<List<DeviceResponse>> list(@RequestParam(required = false) UUID userId) {
        return APIResponse.ok(deviceService.list(userId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<DeviceResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateDeviceRequest req) {
        return APIResponse.ok(deviceService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<Void> delete(@PathVariable UUID id) {
        deviceService.delete(id);
        return APIResponse.ok();
    }
}
