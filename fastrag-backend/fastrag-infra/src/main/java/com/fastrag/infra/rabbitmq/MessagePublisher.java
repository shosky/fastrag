package com.fastrag.infra.rabbitmq;

import com.fastrag.common.handler.GraphBuildHandler;
import com.fastrag.common.handler.IngestionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 消息发布服务
 * 优先通过 RabbitMQ 异步发布，失败时降级为同步直接调用
 *
 * 注意：RabbitTemplate.convertAndSend() 默认是 fire-and-forget 模式，
 * 即使 broker 不可用也不会立即抛异常（消息被缓冲）。因此我们在发送前
 * 主动检测连接状态，不可用时直接走同步降级路径，避免文件处理卡住。
 */
@Service
public class MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(MessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ConnectionFactory connectionFactory;
    private final IngestionHandler ingestionHandler;
    private final GraphBuildHandler graphBuildHandler;

    public MessagePublisher(RabbitTemplate rabbitTemplate,
                           ConnectionFactory connectionFactory,
                           IngestionHandler ingestionHandler,
                           GraphBuildHandler graphBuildHandler) {
        this.rabbitTemplate = rabbitTemplate;
        this.connectionFactory = connectionFactory;
        this.ingestionHandler = ingestionHandler;
        this.graphBuildHandler = graphBuildHandler;
    }

    /**
     * 检测 RabbitMQ 连接是否可用
     */
    private boolean isRabbitMqAvailable() {
        try {
            var conn = connectionFactory.createConnection();
            boolean ok = conn != null && conn.isOpen();
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
            return ok;
        } catch (Exception e) {
            log.warn("RabbitMQ connection check failed: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public void publishIngestion(Object message) {
        if (!isRabbitMqAvailable()) {
            log.warn("RabbitMQ not available, falling back to sync ingestion");
            try {
                ingestionHandler.handleIngestion((Map<String, Object>) message);
                return;
            } catch (Exception ex) {
                log.error("Sync fallback ingestion failed", ex);
                return;
            }
        }
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.DIRECT_EXCHANGE, "ingestion", message);
            log.info("Ingestion message published to RabbitMQ");
        } catch (Exception e) {
            log.warn("RabbitMQ publish failed, falling back to sync call: {}", e.getMessage());
            try {
                ingestionHandler.handleIngestion((Map<String, Object>) message);
            } catch (Exception ex) {
                log.error("Sync fallback ingestion also failed", ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void publishGraphBuild(Object message) {
        String fileIdHint = (message instanceof Map) ? String.valueOf(((Map<?, ?>) message).get("fileId")) : "?";
        log.info("[GraphBuild-Publish] Publishing graph build for fileId={}, message={}", fileIdHint, message);

        if (!isRabbitMqAvailable()) {
            log.warn("[GraphBuild-Publish] RabbitMQ NOT available, falling back to SYNC graph build for fileId={}", fileIdHint);
            try {
                graphBuildHandler.handleGraphBuild((Map<String, Object>) message);
                log.info("[GraphBuild-Publish] Sync fallback graph build completed for fileId={}", fileIdHint);
                return;
            } catch (Exception ex) {
                log.error("[GraphBuild-Publish] Sync fallback graph build FAILED for fileId={}", fileIdHint, ex);
                return;
            }
        }

        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.DIRECT_EXCHANGE, "graph-build", message);
            log.info("[GraphBuild-Publish] Message published to RabbitMQ exchange={}, routingKey=graph-build, fileId={}",
                    RabbitMQConfig.DIRECT_EXCHANGE, fileIdHint);
        } catch (Exception e) {
            log.warn("[GraphBuild-Publish] RabbitMQ publish FAILED for fileId={}, falling back to sync: {}", fileIdHint, e.getMessage());
            try {
                graphBuildHandler.handleGraphBuild((Map<String, Object>) message);
            } catch (Exception ex) {
                log.error("[GraphBuild-Publish] Sync fallback graph build also FAILED for fileId={}", fileIdHint, ex);
            }
        }
    }
}
