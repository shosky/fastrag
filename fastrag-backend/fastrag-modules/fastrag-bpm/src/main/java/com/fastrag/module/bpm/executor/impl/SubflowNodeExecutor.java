package com.fastrag.module.bpm.executor.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.bpm.dto.InstanceTriggerRequest;
import com.fastrag.module.bpm.dto.InstanceVO;
import com.fastrag.module.bpm.entity.BpmFlowDef;
import com.fastrag.module.bpm.entity.BpmFlowVersion;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.enums.VersionStatus;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.NodeExecutor;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import com.fastrag.module.bpm.mapper.BpmFlowDefMapper;
import com.fastrag.module.bpm.mapper.BpmFlowVersionMapper;
import com.fastrag.module.bpm.service.BpmInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 子流程节点:调用已发布的子流程,可同步等待或异步触发。
 *
 * config:
 *  - subFlowDefId:        必填 SpEL string(子流程的 flowDefId)
 *  - versionNo:           可选 Integer,指定子流程版本号(null = 当前发布版本)
 *  - inputMapping:        可选 Map<String,String>,key=子流程 inputParams 的字段名,
 *                         value=SpEL 在当前 ctx.variables 渲染
 *  - outputMapping:       可选 Map<String,String>,key=本节点 outputs 字段名,
 *                         value=SpEL 在子流程 outputs 上下文求值
 *  - waitForCompletion:   可选 boolean,默认 true。true 同步等待子流程到终态
 *  - timeoutMs:           可选,默认 60000。仅同步模式有效
 *
 * outputs: { subInstanceId, status, <outputMapping 字段...> }
 *
 * 安全:嵌套深度超 5 层抛 SUBFLOW_DEPTH_EXCEEDED;子流程未发布抛 SUBFLOW_NOT_FOUND。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubflowNodeExecutor implements NodeExecutor {

    private static final int MAX_DEPTH = 5;
    private static final long POLL_INTERVAL_MS = 500L;
    private static final long DEFAULT_TIMEOUT_MS = 60_000L;

    private final SpelEvaluator spel;
    private final BpmInstanceService instanceService;
    private final BpmFlowDefMapper defMapper;
    private final BpmFlowVersionMapper versionMapper;

    @Override public String type() { return "subflow"; }
    @Override public String category() { return "execute"; }

    @Override
    public void validateConfig(Map<String, Object> config) {
        if (config == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("subflow 节点 config 不能为空");
        if (StrUtil.isBlank((String) config.get("subFlowDefId")))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("subflow 节点 subFlowDefId 必填");
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(ExecutionContext ctx) {
        if (ctx.getSubflowDepth() >= MAX_DEPTH) {
            throw BpmErrorCode.FLOW_SUBFLOW_DEPTH_EXCEEDED.of("subflow 嵌套超过 " + MAX_DEPTH + " 层");
        }

        Map<String, Object> cfg = spel.parseConfig(ctx.getCurrentNode().getConfig());
        Map<String, Object> vars = new HashMap<>(ctx.getVariables() == null ? Map.of() : ctx.getVariables());
        if (ctx.getNodeInputs() != null) vars.putAll(ctx.getNodeInputs());

        String subDefId = String.valueOf(spel.eval((String) cfg.get("subFlowDefId"), vars));
        Integer versionNo = cfg.get("versionNo") instanceof Number n ? n.intValue() : null;
        boolean sync = cfg.get("waitForCompletion") == null || (Boolean) cfg.get("waitForCompletion");
        long timeoutMs = cfg.get("timeoutMs") instanceof Number tn ? tn.longValue() : DEFAULT_TIMEOUT_MS;

        // 校验子流程存在 + 已发布
        BpmFlowDef def = defMapper.selectById(subDefId);
        if (def == null) throw BpmErrorCode.SUBFLOW_NOT_FOUND.of(subDefId);

        BpmFlowVersion ver;
        int resolvedVersionNo;
        if (versionNo != null) {
            ver = versionMapper.selectOne(new LambdaQueryWrapper<BpmFlowVersion>()
                    .eq(BpmFlowVersion::getFlowDefId, subDefId)
                    .eq(BpmFlowVersion::getVersionNo, versionNo));
            resolvedVersionNo = versionNo;
        } else {
            String curVerId = def.getCurrentVersionId();
            if (StrUtil.isBlank(curVerId)) throw BpmErrorCode.SUBFLOW_NOT_FOUND.of(subDefId + " 无当前版本");
            ver = versionMapper.selectById(curVerId);
            resolvedVersionNo = ver == null ? 0 : ver.getVersionNo();
        }
        if (ver == null || !VersionStatus.published.name().equals(ver.getStatus())) {
            throw BpmErrorCode.SUBFLOW_NOT_FOUND.of(subDefId + " v" + resolvedVersionNo + " 未发布");
        }

        // inputMapping 渲染
        Map<String, Object> subInputs = new HashMap<>();
        Object im = cfg.get("inputMapping");
        if (im instanceof Map<?, ?> imm) {
            for (Map.Entry<?, ?> e : imm.entrySet()) {
                if (!(e.getKey() instanceof String k)) continue;
                Object v = e.getValue();
                if (v == null) continue;
                String expr = v instanceof String s ? s : String.valueOf(v);
                try {
                    subInputs.put(k, spel.eval(expr, vars));
                } catch (Exception ex) {
                    subInputs.put(k, null);
                }
            }
        }

        // 触发子流程
        InstanceTriggerRequest req = new InstanceTriggerRequest();
        req.setFlowDefId(subDefId);
        req.setVersionNo(resolvedVersionNo);
        req.setTriggerType("subflow");
        req.setInputParams(subInputs);
        if (StrUtil.isNotBlank(ctx.getOperatorId())) req.setStartUserId(ctx.getOperatorId());

        log.info("SubflowNode: parentInstanceId={}, subDefId={}, v{}, sync={}, depth={}",
                ctx.getInstance() == null ? null : ctx.getInstance().getId(),
                subDefId, resolvedVersionNo, sync, ctx.getSubflowDepth());

        String subInstanceId = instanceService.trigger(req);
        NodeExecutionResult r = new NodeExecutionResult();
        r.getOutputs().put("subInstanceId", subInstanceId);
        r.getOutputs().put("subDefId", subDefId);
        r.getOutputs().put("subVersionNo", resolvedVersionNo);

        if (!sync) {
            r.getOutputs().put("status", "pending");
            return r;
        }

        // 同步等待子流程到终态
        long deadline = System.currentTimeMillis() + timeoutMs;
        InstanceVO last = null;
        while (System.currentTimeMillis() < deadline) {
            last = instanceService.detail(subInstanceId);
            String st = last.getStatus();
            if ("completed".equals(st) || "failed".equals(st) || "cancelled".equals(st)) break;
            try { Thread.sleep(POLL_INTERVAL_MS); }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (last == null) last = instanceService.detail(subInstanceId);
        String finalStatus = last.getStatus();
        r.getOutputs().put("status", finalStatus);
        if ("failed".equals(finalStatus)) {
            throw BpmErrorCode.EXECUTION_FAILED.of("子流程执行失败: " + last.getFailureReason());
        }
        if ("cancelled".equals(finalStatus)) {
            throw BpmErrorCode.EXECUTION_FAILED.of("子流程已取消");
        }

        // outputMapping 渲染
        Object om = cfg.get("outputMapping");
        if (om instanceof Map<?, ?> omm) {
            Map<String, Object> outCtx = last.getOutputs() == null ? new HashMap<>() : new HashMap<>(last.getOutputs());
            for (Map.Entry<?, ?> e : omm.entrySet()) {
                if (!(e.getKey() instanceof String k)) continue;
                Object v = e.getValue();
                if (v == null) continue;
                String expr = v instanceof String s ? s : String.valueOf(v);
                try {
                    r.getOutputs().put(k, spel.eval(expr, outCtx));
                } catch (Exception ex) {
                    r.getOutputs().put(k, null);
                }
            }
        }
        return r;
    }
}