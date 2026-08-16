package com.fastrag.common.annotation;

import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 知识库操作日志注解。
 * <p>标记需要记录业务日志的 Controller 方法，由 {@code KbLogAspect} 切面自动拦截并写入日志表。
 *
 * <p>核心属性：
 * <ul>
 *   <li>{@code category} - 日志分类，对应 {@link com.fastrag.common.enums.LogCategory}，如 operation（操作日志）、retrieval（检索日志）、publish（发布日志）</li>
 *   <li>{@code action} - 操作动作类型，对应 {@link com.fastrag.common.enums.ActionType}，如 kb_created、file_uploaded 等</li>
 *   <li>{@code target} - 操作目标描述，支持 SpEL 表达式（如 {@code #req.name}、{@code #p0}），留空时切面自动推断</li>
 *   <li>{@code detail} - 日志详情描述，同样支持 SpEL 表达式</li>
 * </ul>
 *
 * <p>实现机制：KbLogAspect 通过 AOP 拦截标注了 @Loggable 的方法，在方法执行后收集操作信息，
 * 发布 {@link com.fastrag.common.event.SysAuditLogEvent} 事件，由监听器异步写入 sys_audit_log 表。
 * 这种事件驱动设计避免了日志模块与业务模块之间的循环依赖。
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
