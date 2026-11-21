package com.demo.insightservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("You are an expert energy efficiency advisor. \n" + "Provide concise and practical consumption reduction tips for IoTs, provided their IoT energy consumption pattern").build();
    }
}
