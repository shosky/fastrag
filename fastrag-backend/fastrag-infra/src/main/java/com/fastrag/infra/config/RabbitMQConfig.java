package com.fastrag.infra.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String INGESTION_QUEUE = "ingestion.queue";
    public static final String GRAPH_BUILD_QUEUE = "graph.build.queue";

    @Bean public Queue ingestionQueue() { return QueueBuilder.durable(INGESTION_QUEUE).build(); }
    @Bean public Queue graphBuildQueue() { return QueueBuilder.durable(GRAPH_BUILD_QUEUE).build(); }
    @Bean public Jackson2JsonMessageConverter messageConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate t = new RabbitTemplate(factory);
        t.setMessageConverter(messageConverter());
        return t;
    }
}
