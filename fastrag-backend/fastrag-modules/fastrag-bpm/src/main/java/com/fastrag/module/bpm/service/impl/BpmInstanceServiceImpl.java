package com.fastrag.module.bpm.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.entity.*;
import com.fastrag.module.bpm.enums.*;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.NodeExecutor;
import com.fastrag.module.bpm.executor.NodeExecutorRegistry;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import com.fastrag.module.bpm.mapper.*;
import com.fastrag.module.bpm.service.BpmInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 实例服务 + 内嵌调度器(M3 同步执行;后续可替换为异步 Redis Stream) */
@Slf4j @Service @RequiredArgsConstructor
public class BpmInstanceServiceImpl extends ServiceImpl<BpmFlowInstanceMapper, BpmFlowInstance> implements BpmInstanceService {
    private final BpmFlowDefMapper defMapper;
    private final BpmFlowVersionMapper versionMapper;
    private final BpmFlowNodeMapper nodeMapper;
    private final BpmFlowEdgeMapper edgeMapper;
    private final BpmFlowInstanceEventMapper eventMapper;
    private final NodeExecutorRegistry registry;
    private final SpelEvaluator spel;
    private final ObjectMapper json = new ObjectMapper();

    @Override @Transactional public String trigger(InstanceTriggerRequest req) {
        if (StrUtil.isBlank(req.getFlowDefId())) throw BusinessException.badRequest("flowDefId 必填");
        BpmFlowDef def = defMapper.selectById(req.getFlowDefId());
        if (def == null) throw BpmErrorCode.FLOW_NOT_FOUND.of(req.getFlowDefId());
        BpmFlowVersion ver;
        if (req.getVersionNo() != null) {
            ver = versionMapper.selectOne(new LambdaQueryWrapper<BpmFlowVersion>()
                    .eq(BpmFlowVersion::getFlowDefId, def.getId()).eq(BpmFlowVersion::getVersionNo, req.getVersionNo()));
        } else {
            ver = StrUtil.isBlank(def.getCurrentVersionId()) ? null : versionMapper.selectById(def.getCurrentVersionId());
        }
        if (ver == null) throw BpmErrorCode.FLOW_VERSION_NOT_FOUND.of("current");
        if (!VersionStatus.published.name().equals(ver.getStatus()) && req.getVersionNo() == null)
            throw BpmErrorCode.FLOW_NOT_PUBLISHED.of("v" + ver.getVersionNo());

        BpmFlowInstance ins = new BpmFlowInstance();
        ins.setFlowDefId(def.getId());
        ins.setFlowVersionId(ver.getId());
        ins.setFlowVersionNo(ver.getVersionNo());
        ins.setStatus(InstanceStatus.pending.name());
        ins.setTriggerType(StrUtil.isBlank(req.getTriggerType()) ? "manual" : req.getTriggerType());
        ins.setStartUserId(StrUtil.isBlank(req.getStartUserId()) ? currentUserId() : req.getStartUserId());
        ins.setStartedAt(LocalDateTime.now());
        ins.setTimeoutAt(LocalDateTime.now().plus(Duration.ofMillis(def.getTimeoutMs() == null ? 86400000L : def.getTimeoutMs())));
        ins.setInputParams(toJson(req.getInputParams()));
        baseMapper.insert(ins);
        ins.setTraceId(ins.getId());
        baseMapper.updateById(ins);
        recordEvent(ins, null, EventType.FLOW_STARTED, "info", "流程实例创建并入队", null, null, null);
        // 立即入调度(M3 同步触发,后续替换为异步队列)
        run(ins.getId());
        return ins.getId();
    }

    @Override public PageResult<InstanceVO> list(InstanceListReq req) {
        if (req.getPage() == null || req.getPage() < 1) req.setPage(1);
        if (req.getSize() == null || req.getSize() < 1 || req.getSize() > 100) req.setSize(20);
        LambdaQueryWrapper<BpmFlowInstance> q = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(req.getFlowDefId())) q.eq(BpmFlowInstance::getFlowDefId, req.getFlowDefId());
        if (StrUtil.isNotBlank(req.getStatus())) q.eq(BpmFlowInstance::getStatus, InstanceStatus.of(req.getStatus()).name());
        if (StrUtil.isNotBlank(req.getStartUserId())) q.eq(BpmFlowInstance::getStartUserId, req.getStartUserId());
        q.orderByDesc(BpmFlowInstance::getCreatedAt);
        IPage<BpmFlowInstance> page = baseMapper.selectPage(new Page<>(req.getPage(), req.getSize()), q);
        List<InstanceVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), (int) page.getCurrent(), (int) page.getSize(), records);
    }
    @Override public InstanceVO detail(String instanceId) {
        BpmFlowInstance ins = baseMapper.selectById(instanceId);
        if (ins == null) throw BpmErrorCode.INSTANCE_NOT_FOUND.of(instanceId);
        return toVO(ins);
    }
    @Override public List<?> events(String instanceId) {
        return eventMapper.selectList(new LambdaQueryWrapper<BpmFlowInstanceEvent>()
                .eq(BpmFlowInstanceEvent::getInstanceId, instanceId).orderByAsc(BpmFlowInstanceEvent::getCreatedAt));
    }
    @Override @Transactional public void pause(String instanceId) {
        BpmFlowInstance ins = baseMapper.selectById(instanceId);
        if (ins == null) throw BpmErrorCode.INSTANCE_NOT_FOUND.of(instanceId);
        if (!InstanceStatus.running.name().equals(ins.getStatus()))
            throw BpmErrorCode.INSTANCE_STATE_INVALID.of("仅 running 可暂停,当前 " + ins.getStatus());
        ins.setStatus(InstanceStatus.paused.name());
        baseMapper.updateById(ins);
        recordEvent(ins, null, EventType.FLOW_PAUSED, "info", "用户暂停", null, null, null);
    }
    @Override @Transactional public void resume(String instanceId) {
        BpmFlowInstance ins = baseMapper.selectById(instanceId);
        if (ins == null) throw BpmErrorCode.INSTANCE_NOT_FOUND.of(instanceId);
        if (!InstanceStatus.paused.name().equals(ins.getStatus()))
            throw BpmErrorCode.INSTANCE_STATE_INVALID.of("仅 paused 可恢复,当前 " + ins.getStatus());
        ins.setStatus(InstanceStatus.running.name());
        baseMapper.updateById(ins);
        recordEvent(ins, null, EventType.FLOW_RESUMED, "info", "用户恢复", null, null, null);
        run(instanceId);
    }
    @Override @Transactional public void cancel(String instanceId, String reason) {
        BpmFlowInstance ins = baseMapper.selectById(instanceId);
        if (ins == null) throw BpmErrorCode.INSTANCE_NOT_FOUND.of(instanceId);
        if (InstanceStatus.completed.name().equals(ins.getStatus()) || InstanceStatus.cancelled.name().equals(ins.getStatus()))
            throw BpmErrorCode.INSTANCE_STATE_INVALID.of("已结束的实例不可终止");
        ins.setStatus(InstanceStatus.cancelled.name());
        ins.setFinishedAt(LocalDateTime.now());
        ins.setFailureReason(reason == null ? "用户终止" : reason);
        ins.setDurationMs(ins.getStartedAt() == null ? 0 : Duration.between(ins.getStartedAt(), ins.getFinishedAt()).toMillis());
        baseMapper.updateById(ins);
        recordEvent(ins, null, EventType.FLOW_CANCELLED, "warn", reason, null, null, null);
    }
    @Override @Transactional public void submitUserInput(SubmitInputRequest req) {
        if (StrUtil.isBlank(req.getToken())) throw BusinessException.badRequest("token 必填");
        BpmFlowInstance ins = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowInstance>().eq(BpmFlowInstance::getPendingInputToken, req.getToken()));
        if (ins == null) throw BpmErrorCode.INSTANCE_INPUT_TOKEN_INVALID.of(req.getToken());
        // 把 inputs 写入 variables,清空 pending,继续执行
        Map<String, Object> vars = parseJson(ins.getVariables(), new TypeReference<Map<String, Object>>() {});
        if (vars == null) vars = new HashMap<>();
        if (req.getInputs() != null) vars.put("userInput", req.getInputs());
        ins.setVariables(toJson(vars));
        ins.setPendingInputToken(null);
        ins.setPendingInputForm(null);
        ins.setStatus(InstanceStatus.running.name());
        baseMapper.updateById(ins);
        recordEvent(ins, null, EventType.INPUT_RECEIVED, "info", "收到用户输入", toJson(req.getInputs()), null, null);
        run(ins.getId());
    }

    /** 调度主循环:从 start 节点出发,逐节点执行,直到 end/暂停/失败 */
    @Override @Transactional public void run(String instanceId) {
        BpmFlowInstance ins = baseMapper.selectById(instanceId);
        if (ins == null) return;
        // 仅 pending/running 允许 run
        if (!InstanceStatus.pending.name().equals(ins.getStatus()) && !InstanceStatus.running.name().equals(ins.getStatus())) return;
        ins.setStatus(InstanceStatus.running.name());
        baseMapper.updateById(ins);

        BpmFlowVersion ver = versionMapper.selectById(ins.getFlowVersionId());
        List<BpmFlowNode> allNodes = nodeMapper.selectList(new LambdaQueryWrapper<BpmFlowNode>().eq(BpmFlowNode::getVersionId, ver.getId()));
        if (allNodes.isEmpty()) {
            ins.setStatus(InstanceStatus.failed.name());
            ins.setFailureReason("流程无节点");
            ins.setFinishedAt(LocalDateTime.now());
            baseMapper.updateById(ins);
            recordEvent(ins, null, EventType.FLOW_FAILED, "error", "流程无节点", null, null, null);
            return;
        }
        Map<String, BpmFlowNode> nodeByKey = allNodes.stream().collect(Collectors.toMap(BpmFlowNode::getNodeKey, n -> n, (a, b) -> a));
        List<BpmFlowEdge> allEdges = edgeMapper.selectList(new LambdaQueryWrapper<BpmFlowEdge>().eq(BpmFlowEdge::getVersionId, ver.getId()));
        Map<String, List<BpmFlowEdge>> outgoingBySrc = allEdges.stream()
                .sorted(Comparator.comparing(BpmFlowEdge::getPriority, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(BpmFlowEdge::getSourceNodeKey));
        Map<String, Object> variables = parseJson(ins.getVariables(), new TypeReference<Map<String, Object>>() {});
        if (variables == null) variables = new HashMap<>();
        // 把输入参数也写入 variables
        Map<String, Object> inputMap = parseJson(ins.getInputParams(), new TypeReference<Map<String, Object>>() {});
        if (inputMap != null) variables.put("input", inputMap);
        // 起始节点:找 nodeType=start
        BpmFlowNode start = allNodes.stream().filter(n -> "start".equals(n.getNodeType())).findFirst().orElse(null);
        if (start == null) {
            ins.setStatus(InstanceStatus.failed.name());
            ins.setFailureReason("缺少开始节点");
            baseMapper.updateById(ins);
            recordEvent(ins, null, EventType.FLOW_FAILED, "error", "缺少开始节点", null, null, null);
            return;
        }

        BpmFlowNode current = start;
        Map<String, Object> nodeInputs = new HashMap<>();
        while (current != null) {
            // 暂停/取消检测
            BpmFlowInstance latest = baseMapper.selectById(instanceId);
            if (InstanceStatus.paused.name().equals(latest.getStatus())) { ins.setCurrentNodeKeys(toJson(java.util.List.of(current.getNodeKey()))); baseMapper.updateById(ins); return; }
            if (InstanceStatus.cancelled.name().equals(latest.getStatus())) return;
            if (InstanceStatus.completed.name().equals(latest.getStatus())) return;
            // 节点执行
            NodeExecutionResult result;
            try {
                ExecutionContext ctx = new ExecutionContext();
                ctx.setFlowVersion(ver);
                ctx.setFlowDefId(ins.getFlowDefId());
                ctx.setInstance(ins);
                ctx.setCurrentNode(current);
                ctx.setVariables(variables);
                ctx.setNodeInputs(nodeInputs);
                ctx.setTraceId(ins.getTraceId());
                NodeExecutor executor = registry.getExecutor(current.getNodeType());
                executor.validateConfig(spel.parseConfig(current.getConfig()));
                ctx.setOutgoingEdges(outgoingBySrc.getOrDefault(current.getNodeKey(), java.util.Collections.emptyList()));
                long t0 = System.currentTimeMillis();
                result = executor.execute(ctx);
                long dur = System.currentTimeMillis() - t0;
                recordEvent(ins, current.getNodeKey(), EventType.NODE_COMPLETED, "info", null,
                        toJson(nodeInputs), toJson(result.getOutputs()), (int) dur);
            } catch (Exception ex) {
                result = new NodeExecutionResult();
                result.setSuccess(false);
                result.setErrorMessage(ex.getMessage());
                recordEvent(ins, current.getNodeKey(), EventType.NODE_FAILED, "error", ex.getMessage(), toJson(nodeInputs), null, null);
            }
            // 写出 outputs 到 variables
            if (result.getOutputs() != null) {
                for (Map.Entry<String, Object> e : result.getOutputs().entrySet()) variables.put("node." + current.getNodeKey() + "." + e.getKey(), e.getValue());
                variables.put("node." + current.getNodeKey(), result.getOutputs());
                nodeInputs = result.getOutputs();
            }
            // user_input 等待
            if (result.isWaitingInput()) {
                ins.setVariables(toJson(variables));
                ins.setPendingInputToken(IdUtil.fastSimpleUUID());
                ins.setPendingInputForm(result.getPendingInputForm());
                ins.setCurrentNodeKeys(toJson(java.util.List.of(current.getNodeKey())));
                baseMapper.updateById(ins);
                recordEvent(ins, current.getNodeKey(), EventType.INPUT_REQUESTED, "info", "等待用户输入", null, result.getPendingInputForm(), null);
                return;
            }
            // 失败处理(简化:on_failure=fail 直接终止)
            if (!result.isSuccess()) {
                if ("ignore".equalsIgnoreCase(current.getOnFailure())) {
                    log.warn("Node failed but on_failure=ignore, continuing: {}", current.getNodeKey());
                } else if ("branch".equalsIgnoreCase(current.getOnFailure()) && StrUtil.isNotBlank(current.getFailureBranchNodeKey())) {
                    current = nodeByKey.get(current.getFailureBranchNodeKey());
                    continue;
                } else {
                    ins.setStatus(InstanceStatus.failed.name());
                    ins.setFailureReason(result.getErrorMessage());
                    ins.setFinishedAt(LocalDateTime.now());
                    ins.setDurationMs(ins.getStartedAt() == null ? 0 : Duration.between(ins.getStartedAt(), ins.getFinishedAt()).toMillis());
                    ins.setVariables(toJson(variables));
                    baseMapper.updateById(ins);
                    recordEvent(ins, current.getNodeKey(), EventType.FLOW_FAILED, "error", result.getErrorMessage(), null, null, null);
                    return;
                }
            }
            // 终止节点
            if ("end".equals(current.getNodeType())) {
                ins.setStatus(InstanceStatus.completed.name());
                ins.setOutputParams(toJson(variables));
                ins.setVariables(toJson(variables));
                ins.setCurrentNodeKeys(toJson(java.util.List.of(current.getNodeKey())));
                ins.setFinishedAt(LocalDateTime.now());
                ins.setDurationMs(ins.getStartedAt() == null ? 0 : Duration.between(ins.getStartedAt(), ins.getFinishedAt()).toMillis());
                baseMapper.updateById(ins);
                recordEvent(ins, current.getNodeKey(), EventType.FLOW_COMPLETED, "info", "流程完成", null, toJson(variables), null);
                return;
            }
            // 选下一节点:condition edge 按 SpEL 求值取第一个 true;default/exception/parallel 按 nextHints 或首个
            List<BpmFlowEdge> edges = outgoingBySrc.getOrDefault(current.getNodeKey(), java.util.Collections.emptyList());
            BpmFlowNode next = selectNext(edges, current, variables);
            current = next;
        }
        // 出循环无下一节点 → 视为完成(无 end 节点)
        ins.setStatus(InstanceStatus.completed.name());
        ins.setFinishedAt(LocalDateTime.now());
        ins.setDurationMs(ins.getStartedAt() == null ? 0 : Duration.between(ins.getStartedAt(), ins.getFinishedAt()).toMillis());
        ins.setOutputParams(toJson(variables));
        baseMapper.updateById(ins);
        recordEvent(ins, null, EventType.FLOW_COMPLETED, "info", "流程完成(无 end 节点)", null, toJson(variables), null);
    }

    private BpmFlowNode selectNext(List<BpmFlowEdge> edges, BpmFlowNode from, Map<String, Object> variables) {
        if (edges.isEmpty()) return null;
        for (BpmFlowEdge e : edges) {
            if (StrUtil.isNotBlank(e.getConditionExpr())) {
                try { if (spel.evalBool(e.getConditionExpr(), variables)) return nodeByKey(e.getTargetNodeKey()); }
                catch (Exception ignore) {}
            }
        }
        // 默认边:第一条 edge_kind=default_edge 或 condition 为空
        for (BpmFlowEdge e : edges) {
            if (StrUtil.isBlank(e.getConditionExpr()) || "default_edge".equalsIgnoreCase(e.getEdgeKind()))
                return nodeByKey(e.getTargetNodeKey());
        }
        return nodeByKey(edges.get(0).getTargetNodeKey());
    }
    private BpmFlowNode nodeByKey(String nodeKey) {
        if (StrUtil.isBlank(nodeKey)) return null;
        return nodeMapper.selectOne(new LambdaQueryWrapper<BpmFlowNode>().eq(BpmFlowNode::getNodeKey, nodeKey).last("limit 1"));
    }

    private InstanceVO toVO(BpmFlowInstance ins) {
        InstanceVO vo = new InstanceVO();
        BeanUtils.copyProperties(ins, vo);
        vo.setInputs(parseJson(ins.getInputParams(), new TypeReference<Map<String, Object>>() {}));
        vo.setOutputs(parseJson(ins.getOutputParams(), new TypeReference<Map<String, Object>>() {}));
        if (StrUtil.isNotBlank(ins.getPendingInputToken())) vo.setSubStatus(InstanceStatus.SUBSTATUS_WAITING_INPUT);
        return vo;
    }
    private void recordEvent(BpmFlowInstance ins, String nodeKey, EventType type, String level, String message, String inputSnapshot, String outputSnapshot, Integer durationMs) {
        BpmFlowInstanceEvent e = new BpmFlowInstanceEvent();
        e.setInstanceId(ins.getId());
        e.setTraceId(ins.getTraceId());
        e.setNodeKey(nodeKey);
        e.setEventType(type.name());
        e.setLevel(level == null ? "info" : level);
        e.setMessage(message);
        e.setInputSnapshot(inputSnapshot);
        e.setOutputSnapshot(outputSnapshot);
        e.setDurationMs(durationMs);
        e.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(e);
    }
    private String toJson(Object o) {
        if (o == null) return null;
        try { return json.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }
    private <T> T parseJson(String s, com.fasterxml.jackson.core.type.TypeReference<T> ref) {
        if (StrUtil.isBlank(s)) return null;
        try { return json.readValue(s, ref); } catch (Exception e) { return null; }
    }
    private String currentUserId() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            return auth == null ? "anonymous" : auth.getName();
        } catch (Exception e) { return "anonymous"; }
    }
}