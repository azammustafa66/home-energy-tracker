package com.leetjouney.device_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetjouney.device_service.dto.DeviceDto;
import com.leetjouney.device_service.exception.DeviceNotFoundException;
import com.leetjouney.device_service.model.DeviceType;
import com.leetjouney.device_service.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DeviceService deviceService;

    @Test
    void getDeviceById_returnsOkWithDeviceJson() throws Exception {
        when(deviceService.getDeviceById(1L)).thenReturn(
                DeviceDto.builder().id(1L).name("d1").type(DeviceType.LIGHT)
                        .location("kitchen").userId(9L).build());

        mockMvc.perform(get("/api/v1/device/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("d1"))
                .andExpect(jsonPath("$.type").value("LIGHT"))
                .andExpect(jsonPath("$.userId").value(9));
    }

    @Test
    void getDeviceById_whenServiceThrowsNotFound_returns404() throws Exception {
        when(deviceService.getDeviceById(404L))
                .thenThrow(new DeviceNotFoundException("Device not found with id 404"));

        mockMvc.perform(get("/api/v1/device/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createDevice_returnsOkWithCreatedDto() throws Exception {
        DeviceDto in = DeviceDto.builder()
                .name("new").type(DeviceType.SPEAKER).location("loc").userId(2L).build();
        DeviceDto out = DeviceDto.builder()
                .id(7L).name("new").type(DeviceType.SPEAKER).location("loc").userId(2L).build();
        when(deviceService.createDevice(eq(in))).thenReturn(out);

        mockMvc.perform(post("/api/v1/device/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(in)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void updateDevice_delegatesToServiceAndReturnsBody() throws Exception {
        DeviceDto in = DeviceDto.builder().name("renamed").type(DeviceType.LOCK)
                .location("door").userId(3L).build();
        DeviceDto out = DeviceDto.builder().id(8L).name("renamed").type(DeviceType.LOCK)
                .location("door").userId(3L).build();
        when(deviceService.updateDevice(eq(8L), eq(in))).thenReturn(out);

        mockMvc.perform(put("/api/v1/device/8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(in)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed"));
    }

    @Test
    void deleteDevice_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/device/9"))
                .andExpect(status().isNoContent());

        verify(deviceService).deleteDevice(9L);
    }

    @Test
    void deleteDevice_whenServiceThrowsNotFound_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new DeviceNotFoundException("nope"))
                .when(deviceService).deleteDevice(9L);

        mockMvc.perform(delete("/api/v1/device/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllDevicesByUserId_returnsListJson() throws Exception {
        when(deviceService.getAllDevicesByUserId(5L)).thenReturn(List.of(
                DeviceDto.builder().id(1L).name("a").type(DeviceType.LIGHT).location("x").userId(5L).build(),
                DeviceDto.builder().id(2L).name("b").type(DeviceType.CAMERA).location("y").userId(5L).build()));

        mockMvc.perform(get("/api/v1/device/user/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("a"))
                .andExpect(jsonPath("$[1].type").value("CAMERA"));
    }
}
