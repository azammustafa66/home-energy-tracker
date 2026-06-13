package com.leetjourney.user_service.service;

import com.leetjourney.user_service.dto.UserDto;
import com.leetjourney.user_service.entity.User;
import com.leetjourney.user_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User user(long id) {
        return User.builder()
                .id(id).name("n").surname("s").email("e@x.com")
                .address("a").alerting(true).energyAlertingThreshold(100.0).build();
    }

    @Test
    void createUser_savesEntityFromDtoAndReturnsSavedDto() {
        UserDto input = UserDto.builder()
                .name("Ada").surname("Lovelace").email("ada@x.com")
                .address("addr").alerting(true).energyAlertingThreshold(50.0).build();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(101L);
            return u;
        });

        UserDto out = userService.createUser(input);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        // captor holds reference, id will have been mutated by save stub
        assertThat(captor.getValue().getName()).isEqualTo("Ada");
        assertThat(captor.getValue().getEmail()).isEqualTo("ada@x.com");
        assertThat(out.getId()).isEqualTo(101L);
        assertThat(out.getEnergyAlertingThreshold()).isEqualTo(50.0);
    }

    @Test
    void getUserById_whenPresent_returnsDto() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

        UserDto out = userService.getUserById(1L);

        assertThat(out).isNotNull();
        assertThat(out.getId()).isEqualTo(1L);
        assertThat(out.getEmail()).isEqualTo("e@x.com");
    }

    @Test
    void getUserById_whenAbsent_returnsNull() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThat(userService.getUserById(42L)).isNull();
    }

    @Test
    void updateUser_whenPresent_mutatesEntityAndSaves() {
        User existing = user(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(existing));

        UserDto patch = UserDto.builder()
                .name("New").surname("Name").email("new@x.com")
                .address("New addr").alerting(false).energyAlertingThreshold(999.0).build();

        userService.updateUser(7L, patch);

        verify(userRepository).save(existing);
        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getEmail()).isEqualTo("new@x.com");
        assertThat(existing.isAlerting()).isFalse();
        assertThat(existing.getEnergyAlertingThreshold()).isEqualTo(999.0);
    }

    @Test
    void updateUser_whenAbsent_throwsIllegalArgumentException() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(404L,
                UserDto.builder().name("x").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_whenPresent_callsDelete() {
        User existing = user(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(existing));

        userService.deleteUser(3L);

        verify(userRepository).delete(existing);
    }

    @Test
    void deleteUser_whenAbsent_throwsIllegalArgumentException() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(404L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
