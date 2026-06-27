package com.fastrag.infra.rabbitmq;

import com.fastrag.common.handler.GraphBuildHandler;
import com.fastrag.common.handler.IngestionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 消息发布服务 (原 RabbitMQ Publisher，现改为同步直接调用)
 */
@Slf4j
@Service
public class MessagePublisher {

    private final IngestionHandler ingestionHandler;
    private final GraphBuildHandler graphBuildHandler;

    public MessagePublisher(IngestionHandler ingestionHandler, GraphBuildHandler graphBuildHandler) {
        this.ingestionHandler = ingestionHandler;
        this.graphBuildHandler = graphBuildHandler;
    }

    @SuppressWarnings("unchecked")
    public void publishIngestion(Object message) {
        log.info("Direct call: handleIngestion");
        try {
            ingestionHandler.handleIngestion((Map<String, Object>) message);
        } catch (Exception e) {
            log.error("Ingestion processing failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void publishGraphBuild(Object message) {
        log.info("Direct call: handleGraphBuild");
        try {
            graphBuildHandler.handleGraphBuild((Map<String, Object>) message);
        } catch (Exception e) {
            log.error("Graph build processing failed", e);
        }
    }
}
