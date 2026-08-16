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
 * 消息发布服务，封装基于 RabbitMQ 的异步消息发布能力，并提供同步降级机制。
 *
 * <p>核心职责：将文档处理（Ingestion）和图谱构建（GraphBuild）任务以消息形式异步发布到 RabbitMQ，
 * 由消费者异步执行，从而实现上传与处理的解耦。
 *
 * <p>依赖的外部系统：
 * <ul>
 *   <li>RabbitMQ 消息队列：通过 Spring AMQP 的 {@link RabbitTemplate} 发布消息，
 *       Exchange 和 Queue 定义见 {@link RabbitMQConfig}</li>
 * </ul>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>发送前主动检测 RabbitMQ 连接状态（通过 ConnectionFactory 创建临时连接验证），
 *       避免 RabbitTemplate 的 fire-and-forget 模式导致消息被缓冲后丢失而调用方无感知</li>
 *   <li>RabbitMQ 不可用时自动降级为同步直接调用 Handler（IngestionHandler / GraphBuildHandler），
 *       确保即使消息队列宕机，文档处理流程仍然能够完成</li>
 *   <li>发送失败时同样降级为同步调用，提供双重保障</li>
 * </ul>
 *
 * <p>提供的核心能力：
 * <ul>
 *   <li>{@code publishIngestion} — 发布文档摄取消息到 {@code ingestion} routing key</li>
 *   <li>{@code publishGraphBuild} — 发布图谱构建消息到 {@code graph-build} routing key</li>
 * </ul>
 *
 * <p>与其他模块的交互：被文件上传和知识库处理服务调用，消息消费者在 fastrag-common 模块的
 * Handler 中实现（IngestionHandler、GraphBuildHandler）。
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
