package com.leetjourney.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetjourney.user_service.dto.UserDto;
import com.leetjourney.user_service.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    @Test
    void createUser_returns201WithBody() throws Exception {
        UserDto in = UserDto.builder().name("a").surname("b").email("e@x.com")
                .address("addr").alerting(true).energyAlertingThreshold(10.0).build();
        UserDto out = UserDto.builder().id(1L).name("a").surname("b").email("e@x.com")
                .address("addr").alerting(true).energyAlertingThreshold(10.0).build();
        when(userService.createUser(any(UserDto.class))).thenReturn(out);

        mockMvc.perform(post("/api/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(in)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getUserById_whenPresent_returnsOk() throws Exception {
        when(userService.getUserById(1L)).thenReturn(
                UserDto.builder().id(1L).name("n").email("e@x.com").build());

        mockMvc.perform(get("/api/v1/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getUserById_whenServiceReturnsNull_returns404() throws Exception {
        when(userService.getUserById(404L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/user/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUser_whenServiceSucceeds_returnsOkWithMessage() throws Exception {
        UserDto in = UserDto.builder().name("x").build();

        mockMvc.perform(put("/api/v1/user/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(in)))
                .andExpect(status().isOk())
                .andExpect(content().string("User updated successfully"));

        verify(userService).updateUser(eq(2L), any(UserDto.class));
    }

    @Test
    void updateUser_whenServiceThrowsIllegalArgument_returns404() throws Exception {
        doThrow(new IllegalArgumentException("not found"))
                .when(userService).updateUser(eq(2L), any(UserDto.class));

        UserDto in = UserDto.builder().name("x").build();
        mockMvc.perform(put("/api/v1/user/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(in)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User not found"));
    }

    @Test
    void deleteUser_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/user/3"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(3L);
    }

    @Test
    void deleteUser_whenServiceThrowsIllegalArgument_returns404() throws Exception {
        doThrow(new IllegalArgumentException("nope"))
                .when(userService).deleteUser(3L);

        mockMvc.perform(delete("/api/v1/user/3"))
                .andExpect(status().isNotFound());
    }
}
