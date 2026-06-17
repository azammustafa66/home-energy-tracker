package com.demo.usageservice.config;

import com.demo.common.grpc.device.DeviceServiceGrpc;
import com.demo.common.grpc.energyThreshold.EnergyThresholdServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    DeviceServiceGrpc.DeviceServiceBlockingStub deviceServiceStub(GrpcChannelFactory channels) {
        return DeviceServiceGrpc.newBlockingStub(channels.createChannel("device-service"));
    }

    @Bean
    EnergyThresholdServiceGrpc.EnergyThresholdServiceBlockingStub energyThresholdServiceStub(GrpcChannelFactory channels) {
        return EnergyThresholdServiceGrpc.newBlockingStub(channels.createChannel("user-service"));
    }
}
