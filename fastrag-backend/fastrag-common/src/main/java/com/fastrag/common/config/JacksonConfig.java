package com.fastrag.common.config;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        JavaTimeModule timeModule = new JavaTimeModule();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        timeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(fmt));
        timeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(fmt));

        // 宽松 Integer 反序列化：前端大量表单把 Integer 型 enabled/priority 等字段以布尔 true/false 提交
        // （审核策略、合规规则、质量规则、检索偏好、纠错规则、发布策略、安全策略、字典等），
        // Jackson 2.15 起 boolean→number 默认拒绝且不受 ALLOW_COERCION_OF_SCALARS / CoercionConfig 影响，
        // 反序列化失败会被统一转成「服务异常」，故在此显式兼容 true/false（以及数字字符串）。
        SimpleModule lenient = new SimpleModule("lenientScalars");
        lenient.addDeserializer(Integer.class, new JsonDeserializer<Integer>() {
            @Override public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonToken t = p.currentToken();
                if (t == JsonToken.VALUE_TRUE) return 1;
                if (t == JsonToken.VALUE_FALSE) return 0;
                if (t == JsonToken.VALUE_NULL) return null;
                if (t == JsonToken.VALUE_STRING) {
                    String s = p.getText().trim();
                    if (s.isEmpty()) return null;
                    return Integer.valueOf(s);
                }
                return p.getIntValue();
            }
        });
        return Jackson2ObjectMapperBuilder.json().modules(timeModule, lenient)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
