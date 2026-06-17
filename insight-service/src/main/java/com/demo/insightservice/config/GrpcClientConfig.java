package com.demo.insightservice.config;

import com.demo.common.grpc.device.DeviceServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    DeviceServiceGrpc.DeviceServiceFutureStub deviceServiceFutureStub(GrpcChannelFactory channels) {
        return DeviceServiceGrpc.newFutureStub(channels.createChannel("device-service"));
    }
}
