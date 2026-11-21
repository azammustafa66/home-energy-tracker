package com.demo.userservice.service;

import com.demo.userservice.dto.CreateUserRequest;
import com.demo.userservice.dto.UpdateUserRequest;
import com.demo.userservice.dto.UserResponse;
import com.demo.userservice.entity.User;
import com.demo.userservice.exception.EmailAlreadyUsedException;
import com.demo.userservice.exception.UserNotFoundException;
import com.demo.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserResponse create(CreateUserRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyUsedException(req.email());
        }
        User saved = userRepository.save(User.builder()
                .email(req.email())
                .firstName(req.firstName())
                .lastName(req.lastName())
                .phone(req.phone())
                .alerting(Boolean.TRUE.equals(req.alerting()))
                .energyAlertThreshold(req.energyAlertThreshold())
                .build());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse update(UUID id, UpdateUserRequest req) {
        User user = findOrThrow(id);
        if (req.email() != null && !req.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(req.email())) {
                throw new EmailAlreadyUsedException(req.email());
            }
            user.setEmail(req.email());
        }
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.phone() != null) user.setPhone(req.phone());
        if (req.alerting() != null) user.setAlerting(req.alerting());
        if (req.energyAlertThreshold() != null) user.setEnergyAlertThreshold(req.energyAlertThreshold());
        return UserResponse.from(user);
    }

    public void delete(UUID id) {
        User user = findOrThrow(id);
        userRepository.delete(user);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
