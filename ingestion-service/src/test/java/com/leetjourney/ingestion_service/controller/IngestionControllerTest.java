package com.leetjourney.ingestion_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetjourney.ingestion_service.dto.EnergyUsageDto;
import com.leetjourney.ingestion_service.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private IngestionService ingestionService;

    @Test
    void postIngestion_returns201AndDelegatesToService() throws Exception {
        EnergyUsageDto body = EnergyUsageDto.builder()
                .deviceId(11L)
                .energyConsumed(2.5)
                .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
                .build();

        mockMvc.perform(post("/api/v1/ingestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        ArgumentCaptor<EnergyUsageDto> captor = ArgumentCaptor.forClass(EnergyUsageDto.class);
        verify(ingestionService).ingestEnergyUsage(captor.capture());
        assertThat(captor.getValue().deviceId()).isEqualTo(11L);
        assertThat(captor.getValue().energyConsumed()).isEqualTo(2.5);
        assertThat(captor.getValue().timestamp()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void postIngestion_withMissingContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/ingestion")
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(ingestionService);
    }

    @Test
    void postIngestion_withMalformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ingestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ingestionService);
    }
}
