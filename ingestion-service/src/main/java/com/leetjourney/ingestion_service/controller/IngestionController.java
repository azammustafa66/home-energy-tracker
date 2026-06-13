package com.leetjourney.ingestion_service.controller;

import com.leetjourney.ingestion_service.dto.EnergyUsageDto;
import com.leetjourney.ingestion_service.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
public class IngestionController {
    private final IngestionService ingestionService;

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public void ingestData(@RequestBody EnergyUsageDto usageDto) {
        ingestionService.ingestEnergyUsage(usageDto);
    }
}
