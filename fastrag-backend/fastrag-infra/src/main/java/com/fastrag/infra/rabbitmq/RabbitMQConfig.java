package com.fastrag.infra.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String INGESTION_QUEUE = "fastrag.ingestion.queue";
    public static final String GRAPH_BUILD_QUEUE = "fastrag.graph-build.queue";
    public static final String DIRECT_EXCHANGE = "fastrag.direct";

    @Bean
    public TopicExchange fastragExchange() {
        return new TopicExchange(DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public Queue ingestionQueue() {
        return new Queue(INGESTION_QUEUE, true);
    }

    @Bean
    public Queue graphBuildQueue() {
        return new Queue(GRAPH_BUILD_QUEUE, true);
    }

    @Bean
    public Binding ingestionBinding(Queue ingestionQueue, TopicExchange exchange) {
        return BindingBuilder.bind(ingestionQueue).to(exchange).with("ingestion");
    }

    @Bean
    public Binding graphBuildBinding(Queue graphBuildQueue, TopicExchange exchange) {
        return BindingBuilder.bind(graphBuildQueue).to(exchange).with("graph-build");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
