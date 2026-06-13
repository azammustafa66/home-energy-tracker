package com.leetjouney.device_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetjouney.device_service.dto.DeviceDto;
import com.leetjouney.device_service.model.DeviceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: full CRUD against DeviceController backed by MySQL Testcontainer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class DeviceControllerIT {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("home_energy_tracker")
                    .withUsername("root")
                    .withPassword("password");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void crudFlow() throws Exception {
        DeviceDto dto = DeviceDto.builder()
                .name("Living Room Lamp")
                .type(DeviceType.LIGHT)
                .location("Living Room")
                .userId(1L)
                .build();

        MvcResult created = mockMvc.perform(post("/api/v1/device/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        Long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        assertThat(id).isPositive();

        mockMvc.perform(get("/api/v1/device/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Living Room Lamp"))
                .andExpect(jsonPath("$.type").value("LIGHT"));

        mockMvc.perform(get("/api/v1/device/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        DeviceDto update = DeviceDto.builder()
                .name("Kitchen Lamp")
                .type(DeviceType.LIGHT)
                .location("Kitchen")
                .userId(1L)
                .build();
        mockMvc.perform(put("/api/v1/device/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Kitchen"));

        mockMvc.perform(delete("/api/v1/device/" + id))
                .andExpect(status().isNoContent());
    }
}
