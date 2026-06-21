#!/bin/bash
# FastRAG Backend Source Generator
BASE="D:/Workspace/java/github/rag/fastrag/fastrag-backend"

# ==================== fastrag-common ====================
C="$BASE/fastrag-common/src/main/java/com/fastrag/common"

mkdir -p "$C/response" "$C/enums" "$C/exception" "$C/base" "$C/config" "$C/util"

cat > "$C/response/ApiResponse.java" << 'EOF'
package com.fastrag.common.response;
import lombok.Data;
import java.io.Serializable;
@Data
public class ApiResponse<T> implements Serializable {
    private int code;
    private T data;
    private String message;
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(200); r.setData(data); r.setMessage("success");
        return r;
    }
    public static <T> ApiResponse<T> success() { return success(null); }
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(code); r.setMessage(message);
        return r;
    }
    public static <T> ApiResponse<T> badRequest(String msg) { return error(400, msg); }
    public static <T> ApiResponse<T> unauthorized(String msg) { return error(401, msg != null ? msg : "未认证"); }
    public static <T> ApiResponse<T> forbidden(String msg) { return error(403, msg != null ? msg : "权限不足"); }
    public static <T> ApiResponse<T> notFound(String msg) { return error(404, msg != null ? msg : "资源不存在"); }
    public static <T> ApiResponse<T> serverError(String msg) { return error(500, msg != null ? msg : "服务异常"); }
}
EOF

cat > "$C/response/PageResult.java" << 'EOF'
package com.fastrag.common.response;
import lombok.Data;
import java.io.Serializable;
import java.util.List;
@Data
public class PageResult<T> implements Serializable {
    private List<T> list;
    private long total;
    private int page;
    private int pageSize;
    public static <T> PageResult<T> of(List<T> list, long total, int page, int pageSize) {
        PageResult<T> r = new PageResult<>();
        r.setList(list); r.setTotal(total); r.setPage(page); r.setPageSize(pageSize);
        return r;
    }
}
EOF

cat > "$C/response/PageParams.java" << 'EOF'
package com.fastrag.common.response;
import lombok.Data;
import jakarta.validation.constraints.Min;
@Data
public class PageParams {
    @Min(1) private int page = 1;
    @Min(1) private int pageSize = 20;
    private String keyword;
}
EOF

for enum_name in FileCategory ProcessStatus KBRole PublishStatus ReviewStatus NodeType ToolType LogCategory UpdateType; do
cat > "$C/enums/$enum_name.java" << EOF
package com.fastrag.common.enums;
public enum $enum_name {
EOF
case $enum_name in
  FileCategory) echo "  document, image, audio, video" >> "$C/enums/$enum_name.java" ;;
  ProcessStatus) echo "  pending, processing, completed, failed" >> "$C/enums/$enum_name.java" ;;
  KBRole) echo "  owner, editor, viewer" >> "$C/enums/$enum_name.java" ;;
  PublishStatus) echo "  draft, pending_review, approved, published, rejected" >> "$C/enums/$enum_name.java" ;;
  ReviewStatus) echo "  pending, approved, rejected, timeout" >> "$C/enums/$enum_name.java" ;;
  NodeType) echo "  start, end, llm, knowledge_retrieval, intent, selector, plugin" >> "$C/enums/$enum_name.java" ;;
  ToolType) echo "  builtin, knowledge, http" >> "$C/enums/$enum_name.java" ;;
  LogCategory) echo "  operation, retrieval, publish" >> "$C/enums/$enum_name.java" ;;
  UpdateType) echo "  file_added, file_removed, file_updated, chunk_added, chunk_removed, chunk_updated, config_changed" >> "$C/enums/$enum_name.java" ;;
esac
echo "}" >> "$C/enums/$enum_name.java"
done

cat > "$C/enums/ModelPurpose.java" << 'EOF'
package com.fastrag.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
public enum ModelPurpose {
    LLM("大语言模型"), EMBEDDING("Embedding模型"), RERANK("Rerank模型"), OCR("OCR识别");
    private final String label;
    ModelPurpose(String label) { this.label = label; }
    @JsonValue public String getLabel() { return label; }
}
EOF

cat > "$C/exception/BusinessException.java" << 'EOF'
package com.fastrag.common.exception;
import lombok.Getter;
@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String message) { super(message); this.code = code; }
    public static BusinessException badRequest(String msg) { return new BusinessException(400, msg); }
    public static BusinessException unauthorized(String msg) { return new BusinessException(401, msg); }
    public static BusinessException forbidden(String msg) { return new BusinessException(403, msg); }
    public static BusinessException notFound(String msg) { return new BusinessException(404, msg); }
    public static BusinessException serverError(String msg) { return new BusinessException(500, msg); }
}
EOF

cat > "$C/exception/GlobalExceptionHandler.java" << 'EOF'
package com.fastrag.common.exception;
import com.fastrag.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleBusiness(BusinessException e) {
        log.warn("Business exception: code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("参数校验失败");
        return ApiResponse.badRequest(msg);
    }
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("参数绑定失败");
        return ApiResponse.badRequest(msg);
    }
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ApiResponse.serverError("服务异常");
    }
}
EOF

cat > "$C/base/BaseEntity.java" << 'EOF'
package com.fastrag.common.base;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
@Data
public abstract class BaseEntity implements Serializable {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
EOF

cat > "$C/config/JacksonConfig.java" << 'EOF'
package com.fastrag.common.config;
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
EOF

cat > "$C/config/WebMvcConfig.java" << 'EOF'
package com.fastrag.common.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns("*")
                .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
                .allowedHeaders("*").allowCredentials(true).maxAge(3600);
    }
}
EOF

cat > "$C/util/IdGenerator.java" << 'EOF'
package com.fastrag.common.util;
import cn.hutool.core.util.IdUtil;
public class IdGenerator {
    public static String nextId() { return IdUtil.fastSimpleUUID(); }
}
EOF

echo "fastrag-common done"
