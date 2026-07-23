package com.fastrag.common.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${storage.local.path:./uploads}")
    private String uploadPath;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns("*")
                .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
                .allowedHeaders("*").allowCredentials(true).maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射上传文件到 /api/files/** 可访问
        registry.addResourceHandler("/api/files/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
