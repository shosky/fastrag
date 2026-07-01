package com.fastrag.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiGatewayConfig {
    @Value("${ai.gateway.url:http://localhost:11434}") private String gatewayUrl;

    @Bean
    public WebClient aiWebClient() {
        // 增大缓冲区限制至 20MB，避免大响应被截断
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(gatewayUrl)
                .exchangeStrategies(strategies)
                .build();
    }
}
