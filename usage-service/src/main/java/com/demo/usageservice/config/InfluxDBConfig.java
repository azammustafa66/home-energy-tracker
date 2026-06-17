package com.demo.usageservice.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDBConfig {

    @Value("${influxDB.url}")
    private String influxDBURL;
    @Value("${influxDB.token}")
    private String influxDBToken;
    @Value("${influxDB.org}")
    private String influxDBOrg;

    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(influxDBURL, influxDBToken.toCharArray(), influxDBOrg);
    }
}
