package com.fastrag.infra.rabbitmq;

import com.fastrag.infra.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePublisher {
    private final RabbitTemplate rabbitTemplate;

    public void publishIngestion(Object message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.INGESTION_QUEUE, message);
        log.info("Published ingestion message");
    }

    public void publishGraphBuild(Object message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.GRAPH_BUILD_QUEUE, message);
        log.info("Published graph build message");
    }
}
