package com.demo.userservice.controller;

import com.demo.common.api.APIResponse;
import com.demo.userservice.dto.CreateUserRequest;
import com.demo.userservice.dto.UpdateUserRequest;
import com.demo.userservice.dto.UserResponse;
import com.demo.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<APIResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest req) {
        UserResponse created = userService.create(req);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.id()))
                .body(APIResponse.ok(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.token.subject")
    public APIResponse<UserResponse> get(@PathVariable UUID id) {
        return APIResponse.ok(userService.get(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<List<UserResponse>> list() {
        return APIResponse.ok(userService.list());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.token.subject")
    public APIResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return APIResponse.ok(userService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return APIResponse.ok();
    }
}
