package com.demo.ingestionservice.controller;

import com.demo.ingestionservice.dto.EnergyUsageDTO;
import com.demo.ingestionservice.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('INGESTION', 'ADMIN')")
    public void ingestEnergyUsage(@RequestBody EnergyUsageDTO usageDTO) {
        ingestionService.ingestEnergyUsage(usageDTO);
    }
}
