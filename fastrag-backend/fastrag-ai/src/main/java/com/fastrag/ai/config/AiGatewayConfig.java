package com.fastrag.ai.config;

/**
 * AI 网关配置类，负责创建和配置统一的 AI 服务 HTTP 客户端基础设施。
 *
 * <p>本配置类是 fastrag-ai 模块的核心配置，为 {@link com.fastrag.ai.llm.LlmService}、
 * {@link com.fastrag.ai.embedding.EmbeddingService}、
 * {@link com.fastrag.ai.rerank.RerankService} 等服务提供共享的 HTTP 客户端 Bean。</p>
 *
 * <p>核心职责：
 * <ul>
 *   <li>创建带代理支持的 Reactor Netty {@link HttpClient}（{@link #aiHttpClient}）</li>
 *   <li>创建统一的 {@link WebClient}（{@link #aiWebClient}），配置基础 URL、代理连接器和缓冲区</li>
 * </ul>
 *
 * <p>代理配置：
 * <ul>
 *   <li>通过 {@code ai.proxy.enabled} 控制是否启用 HTTP 代理</li>
 *   <li>支持 non-proxy-hosts 配置，命中该列表的域名走直连（支持通配符，如 *.siliconflow.cn）</li>
 *   <li>代理对部分服务不稳定/不通时可在 non-proxy-hosts 中绕过</li>
 * </ul>
 *
 * <p>WebClient 配置：
 * <ul>
 *   <li>使用 {@link ReactorClientHttpConnector} 连接 Reactor Netty HttpClient，支持代理</li>
 *   <li>内存缓冲区上限设置为 20MB，避免大响应（如长文本 Embedding）被截断</li>
 *   <li>baseURL 默认为 {@code localhost:11434}（Ollama 本地模型），可通过配置指向远程网关</li>
 * </ul>
 *
 * <p>配置项（application.yml）：
 * <ul>
 *   <li>{@code ai.gateway.url} - AI 网关基础 URL，默认 localhost:11434</li>
 *   <li>{@code ai.proxy.enabled} - 是否启用代理，默认 false</li>
 *   <li>{@code ai.proxy.host} - 代理主机地址</li>
 *   <li>{@code ai.proxy.port} - 代理端口</li>
 *   <li>{@code ai.proxy.non-proxy-hosts} - 不走代理的域名列表（逗号分隔，支持 * 通配）</li>
 * </ul>
 *
 * <p>模块交互：本配置类创建的 Bean 被以下模块直接注入使用：
 * <ul>
 *   <li>fastrag-ai 内部的 LlmService、EmbeddingService、RerankService</li>
 *   <li>fastrag-tools 模块的 HttpToolExecutor</li>
 * </ul>
 */
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;

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
     *
     * <p>连接池必须配置空闲淘汰（maxIdleTime + evictInBackground）：远端网关/跨境 NAT
     * 会静默掐掉空闲数十秒的 keep-alive 连接，若复用已死连接会在读响应时报
     * Connection reset（解析分片等长流程中连接空闲几十秒后必现）。</p>
     */
    @Bean
    public HttpClient aiHttpClient() {
        reactor.netty.resources.ConnectionProvider provider = reactor.netty.resources.ConnectionProvider
                .builder("ai-gateway")
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofMinutes(5))
                .evictInBackground(Duration.ofSeconds(30))
                .lifo()
                .build();
        HttpClient client = HttpClient.create(provider)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000);
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
