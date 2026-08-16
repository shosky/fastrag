package com.fastrag.infra.rabbitmq;

/**
 * RabbitMQ 消息队列配置类，定义 Exchange、Queue、Binding 及消息序列化方式。
 *
 * <p>配置的组件：
 * <ul>
 *   <li>TopicExchange（{@code fastrag.direct}）— 持久化、非自动删除的主题交换机</li>
 *   <li>Queue（{@code fastrag.ingestion.queue}）— 文档摄取任务队列，持久化</li>
 *   <li>Queue（{@code fastrag.graph-build.queue}）— 图谱构建任务队列，持久化</li>
 *   <li>Binding — 将队列绑定到交换机，routing key 分别为 {@code ingestion} 和 {@code graph-build}</li>
 *   <li>MessageConverter（Jackson2JsonMessageConverter）— 使用 Jackson 将消息对象序列化为 JSON</li>
 *   <li>RabbitTemplate — 配置了 JSON 消息转换器的 RabbitMQ 操作模板</li>
 * </ul>
 *
 * <p>生效方式：通过 {@code @Configuration} 注解被 Spring 自动扫描加载，RabbitMQ 连接参数
 * 来自 {@code application.yml} 中的 {@code spring.rabbitmq.*} 前缀配置。
 *
 * <p>与其他模块的交互：配置的 RabbitTemplate Bean 被 {@link MessagePublisher} 注入使用；
 * 队列消费者在 fastrag-common 模块中通过 {@code @RabbitListener} 注解绑定消费。
 */

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
