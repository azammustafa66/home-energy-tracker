package com.leetjourney.user_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetjourney.user_service.dto.UserDto;
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
 * Integration test: full CRUD against UserController, backed by MySQL Testcontainer
 * with Flyway-managed schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class UserControllerIT {

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
        UserDto dto = UserDto.builder()
                .name("Ada")
                .surname("Lovelace")
                .email("ada+" + System.nanoTime() + "@example.com")
                .address("221B Baker Street")
                .alerting(true)
                .energyAlertingThreshold(123.45)
                .build();

        MvcResult created = mockMvc.perform(post("/api/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        Long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        assertThat(id).isPositive();

        mockMvc.perform(get("/api/v1/user/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada"))
                .andExpect(jsonPath("$.alerting").value(true));

        UserDto update = UserDto.builder()
                .name("Grace")
                .surname("Hopper")
                .email(dto.getEmail())
                .address("Navy HQ")
                .alerting(false)
                .energyAlertingThreshold(50.0)
                .build();

        mockMvc.perform(put("/api/v1/user/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/user/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Grace"));

        mockMvc.perform(delete("/api/v1/user/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/user/" + id))
                .andExpect(status().isNotFound());
    }
}
