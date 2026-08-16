package com.fastrag.common.config;

/**
 * Jackson 全局序列化配置。
 * <p>自定义 Spring Boot 默认的 ObjectMapper，统一系统中所有 JSON 序列化/反序列化的行为规范。
 *
 * <p>核心配置内容：
 * <ul>
 *   <li>注册 {@code JavaTimeModule}，支持 Java 8 日期时间类型的正确序列化</li>
 *   <li>将 {@code LocalDateTime} 的序列化格式统一为 {@code yyyy-MM-dd HH:mm:ss}，而非默认的时间戳格式</li>
 *   <li>禁用 {@code WRITE_DATES_AS_TIMESTAMPS}，确保日期以可读的字符串形式输出</li>
 * </ul>
 *
 * <p>影响范围：此配置作为全局 Bean 注入后，将影响所有使用 Jackson 进行 JSON 处理的场景，
 * 包括 REST API 的请求/响应序列化、数据库 JSON 字段的读写、消息队列消息体的序列化等。
 *
 * <p>与其他配置的关系：配合 {@link WebMvcConfig} 一起构成 Web 层的基础配置体系。
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        JavaTimeModule module = new JavaTimeModule();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(fmt));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(fmt));
        return Jackson2ObjectMapperBuilder.json().modules(module)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }
}
