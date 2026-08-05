package com.fastrag.module.publish.aspect;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.event.SysAuditLogEvent;
import com.fastrag.common.util.IpUtil;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class KbLogAspect {

    private final LogService logService;
    private final ApplicationEventPublisher eventPublisher;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    private static final Map<String, String> MODULE_BY_PREFIX = Map.ofEntries(
        Map.entry("kb_", "知识库"),
        Map.entry("file_", "知识库"),
        Map.entry("folder_", "知识库"),
        Map.entry("qa_pair_", "知识库"),
        Map.entry("chunk_", "知识库"),
        Map.entry("strategy_", "知识库"),
        Map.entry("graph_", "知识图谱"),
        Map.entry("evaluation_", "评测中心"),
        Map.entry("benchmark_", "评测中心"),
        Map.entry("publish_", "知识发布"),
        Map.entry("app_", "应用中心"),
        Map.entry("workflow_", "工作流"),
        Map.entry("agent_", "智能体"),
        Map.entry("user_", "系统管理"),
        Map.entry("role_", "系统管理"),
        Map.entry("permission_", "系统管理"),
        Map.entry("config_", "系统管理"),
        Map.entry("security_policy_", "系统管理"),
        Map.entry("publish_strategy_", "系统管理"),
        Map.entry("model_", "模型管理"),
        Map.entry("tool_", "工具管理"),
        Map.entry("skill_", "技能管理"),
        Map.entry("mcp_service_", "MCP管理"),
        Map.entry("db_instance_", "数据库管理"),
        Map.entry("conversation_", "应用中心"),
        Map.entry("reset_config_", "知识发布")
    );

    private static final Map<LogCategory, String> MODULE_BY_CATEGORY = Map.of(
        LogCategory.operation, "知识库",
        LogCategory.retrieval, "知识检索",
        LogCategory.publish, "知识发布"
    );

    @Around("@annotation(loggable)")
    public Object around(ProceedingJoinPoint pjp, Loggable loggable) throws Throwable {
        String operator = resolveOperator();
        String userId = resolveUserId();
        String kbId = resolveKbId(pjp);
        String target = resolveSpelOrFallback(pjp, loggable.target(), this::resolveTargetFallback);
        String detail = resolveSpelOrFallback(pjp, loggable.detail(), ctx -> loggable.detail());
        String ip = IpUtil.getClientIp();
        String module = resolveModule(loggable);

        try {
            Object result = pjp.proceed();
            logService.addLog(kbId, loggable.category(), loggable.action(), target,
                    detail, operator, "success", null);
            publishAuditEvent(userId, operator, module, loggable.action(), target, detail, "success", ip);
            return result;
        } catch (Exception e) {
            String errorDetail = detail;
            if (errorDetail == null || errorDetail.isEmpty()) {
                errorDetail = e.getMessage() != null ? e.getMessage() : "unknown error";
            }
            log.warn("[LogAspect] Action {} failed on kbId={}: {}", loggable.action(), kbId, e.getMessage());
            logService.addLog(kbId, loggable.category(), loggable.action(), target,
                    errorDetail, operator, "failed", null);
            publishAuditEvent(userId, operator, module, loggable.action(), target, errorDetail, "failed", ip);
            throw e;
        }
    }

    private String resolveModule(Loggable loggable) {
        if (loggable.action() != null) {
            String name = loggable.action().name();
            for (Map.Entry<String, String> entry : MODULE_BY_PREFIX.entrySet()) {
                if (name.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return MODULE_BY_CATEGORY.getOrDefault(loggable.category(), "其他");
    }

    private void publishAuditEvent(String userId, String username, String module,
                                   ActionType action, String target, String detail,
                                   String status, String ip) {
        try {
            eventPublisher.publishEvent(new SysAuditLogEvent(
                    this, userId, username, module, action, target, detail, status, ip));
        } catch (Exception e) {
            log.warn("[LogAspect] Failed to publish audit event: {}", e.getMessage());
        }
    }


    private String resolveSpelOrFallback(ProceedingJoinPoint pjp, String expr,
                                         java.util.function.Function<ProceedingJoinPoint, String> fallback) {
        if (expr == null || expr.isEmpty()) {
            return fallback.apply(pjp);
        }
        if (!expr.startsWith("#")) {
            return expr;
        }
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Method method = signature.getMethod();
            Parameter[] parameters = method.getParameters();
            Object[] args = pjp.getArgs();
            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
            }
            for (int i = 0; i < parameters.length; i++) {
                context.setVariable(parameters[i].getName(), args[i]);
            }
            Expression spelExpr = spelParser.parseExpression(expr);
            Object value = spelExpr.getValue(context);
            if (value != null) {
                return value.toString();
            }
            return fallback.apply(pjp);
        } catch (Exception e) {
            log.warn("[LogAspect] SpEL evaluation failed for '{}': {}", expr, e.getMessage());
            return fallback.apply(pjp);
        }
    }

    private String resolveKbId(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if ("kbId".equals(parameters[i].getName())) {
                if (args[i] instanceof String s) return s;
            }
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String s && !s.isEmpty()) return s;
        }
        return "unknown";
    }

    private String resolveTargetFallback(ProceedingJoinPoint pjp) {
        String[] idParamNames = {"fileId", "folderId", "benchmarkId", "evaluationId",
                "strategyId", "knowledgeId", "planId", "id"};
        Object[] args = pjp.getArgs();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            for (String name : idParamNames) {
                if (name.equals(parameters[i].getName()) && args[i] instanceof String s && !s.isEmpty()) {
                    return s;
                }
            }
        }
        return "";
    }

    private String resolveOperator() {
        try {
            return SecurityUtil.getCurrentUser() != null
                    ? SecurityUtil.getCurrentUser().getUsername() : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    private String resolveUserId() {
        try {
            return SecurityUtil.getCurrentUser() != null
                    ? SecurityUtil.getCurrentUserId() : "system";
        } catch (Exception e) {
            return "system";
        }
    }
}
