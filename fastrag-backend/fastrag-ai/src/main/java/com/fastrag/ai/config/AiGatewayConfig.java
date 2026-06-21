package com.fastrag.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiGatewayConfig {
    @Value("${ai.gateway.url:http://localhost:11434}") private String gatewayUrl;

    @Bean
    public WebClient aiWebClient() {
        return WebClient.builder().baseUrl(gatewayUrl).build();
    }
}
