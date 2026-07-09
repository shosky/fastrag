package com.fastrag.common.annotation;

import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

import java.lang.annotation.*;

/**
 * 知识库操作日志注解
 * <p>标记需要记录业务日志的 Controller 方法，由 {@code KbLogAspect} 切面自动拦截写入。
 *
 * <p>使用示例：
 * <pre>
 * {@code @Loggable(category = LogCategory.operation, action = ActionType.kb_created)}
 * {@code @PostMapping}
 * public ApiResponse<?> create(@Valid @RequestBody KbCreateRequest req) {
 *     return ApiResponse.success(svc.create(req, SecurityUtil.getCurrentUserId()));
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Loggable {

    /** 日志分类 */
    LogCategory category();

    /** 操作动作类型 */
    ActionType action();

    /**
     * 操作目标描述，支持 SpEL 表达式
     * <p>可用变量：{@code #p0}, {@code #p1} ... 按位置引用方法参数
     * <p>可用变量：{@code #req.name} 按参数名引用（需要编译时参数名信息）
     * <p>留空时切面会自动尝试查找参数名为 fileId/folderId/... 的值作为 target
     * <p>默认值：{@code ""}
     */
    String target() default "";

    /**
     * 日志详情描述，支持 SpEL 表达式
     * <p>默认值：{@code ""}
     */
    String detail() default "";
}
