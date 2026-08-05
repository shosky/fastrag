package com.fastrag.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class AiGatewayConfig {
    @Value("${ai.gateway.url:http://localhost:11434}") private String gatewayUrl;

    @Value("${ai.proxy.enabled:false}") private boolean proxyEnabled;
    @Value("${ai.proxy.host:}") private String proxyHost;
    @Value("${ai.proxy.port:0}") private int proxyPort;
    /** 不走代理的域名列表（逗号分隔，支持 * 通配，如 *.siliconflow.cn），默认空 */
    @Value("${ai.proxy.non-proxy-hosts:}") private String proxyNonProxyHosts;

    /**
     * 创建带代理支持的 Reactor Netty HttpClient。
     * 当 ai.proxy.enabled=true 时，所有使用此 HttpClient 的 WebClient 都会走代理；
     * 命中 non-proxy-hosts 的域名直连（代理对部分服务不稳定/不通时可在此绕过）。
     */
    @Bean
    public HttpClient aiHttpClient() {
        HttpClient client = HttpClient.create();
        if (proxyEnabled && proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            client = client.proxy(proxySpec -> {
                reactor.netty.transport.ProxyProvider.Builder builder = proxySpec
                        .type(reactor.netty.transport.ProxyProvider.Proxy.HTTP)
                        .host(proxyHost)
                        .port(proxyPort);
                if (proxyNonProxyHosts != null && !proxyNonProxyHosts.isBlank()) {
                    builder.nonProxyHosts(proxyNonProxyHosts);
                }
            });
        }
        return client;
    }

    @Bean
    public WebClient aiWebClient(HttpClient aiHttpClient) {
        // 增大缓冲区限制至 20MB，避免大响应被截断
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(gatewayUrl)
                .clientConnector(new ReactorClientHttpConnector(aiHttpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
