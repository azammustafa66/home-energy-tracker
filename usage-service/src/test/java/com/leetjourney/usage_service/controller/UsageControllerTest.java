package com.leetjourney.usage_service.controller;

import com.leetjourney.usage_service.dto.DeviceDto;
import com.leetjourney.usage_service.dto.UsageDto;
import com.leetjourney.usage_service.service.UsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsageController.class)
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsageService usageService;

    @Test
    void getUserDeviceUsage_returnsOkWithDefaultDaysOf3() throws Exception {
        UsageDto dto = UsageDto.builder()
                .userId(42L)
                .devices(List.of(DeviceDto.builder()
                        .id(1L).name("d").type("LIGHT").location("loc")
                        .userId(42L).energyConsumed(12.5).build()))
                .build();
        when(usageService.getXDaysUsageForUser(42L, 3)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/usage/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.devices[0].id").value(1))
                .andExpect(jsonPath("$.devices[0].energyConsumed").value(12.5));

        verify(usageService).getXDaysUsageForUser(eq(42L), eq(3));
    }

    @Test
    void getUserDeviceUsage_honoursExplicitDaysQueryParam() throws Exception {
        UsageDto dto = UsageDto.builder().userId(5L).devices(List.of()).build();
        when(usageService.getXDaysUsageForUser(5L, 14)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/usage/5?days=14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5));

        verify(usageService).getXDaysUsageForUser(5L, 14);
    }
}
