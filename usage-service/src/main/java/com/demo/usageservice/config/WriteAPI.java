package com.demo.usageservice.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class WriteAPI {

    private final InfluxDBClient client;

    @Bean
    WriteApi writeApi() {
        return client.makeWriteApi(
                WriteOptions.builder().batchSize(5000).flushInterval(5000).build()
        );
    }
}
