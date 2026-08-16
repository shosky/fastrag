package com.fastrag.common.config;

/**
 * Spring Web MVC 全局配置。
 * <p>自定义 Web 层的跨域策略和静态资源映射，为前端提供统一的访问入口。
 *
 * <p>核心配置内容：
 * <ul>
 *   <li><b>CORS 跨域配置</b> - 对所有 {@code /api/**} 路径开放跨域访问，允许所有来源、常用 HTTP 方法、
 *       所有请求头，并启用凭证传递。预检请求缓存 3600 秒以减少 OPTIONS 请求开销。</li>
 *   <li><b>静态资源映射</b> - 将本地文件上传目录映射到 {@code /api/files/**} URL 路径，
 *       使上传的文件可以通过 HTTP 直接访问。上传路径通过配置项 {@code storage.local.path} 指定，默认为 {@code ./uploads}。</li>
 * </ul>
 *
 * <p>设计意图：将跨域和资源映射集中管理，避免在各 Controller 中分散配置。
 * 上传文件的 URL 访问路径统一为 {@code /api/files/xxx}，方便前端拼接和使用。
 *
 * <p>与其他配置的关系：配合 {@link JacksonConfig} 一起构成 Web 层的基础配置体系。
 */
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
