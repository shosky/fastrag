package com.fastrag.module.bpm.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.NodeRequest;
import com.fastrag.module.bpm.entity.WfDebugLog;
import com.fastrag.module.bpm.mapper.BpmWfDebugLogMapper;
import com.fastrag.module.bpm.service.BpmFlowNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BPM 流程节点 CRUD。节点生命周期（创建/更新/删除/配置变更）自动写入 wf_debug_log
 * （context='{"kind":"node"}'，与业务流管理画布的「节点日志」同源），供画布「节点日志」弹窗查看。
 */
@RestController @RequestMapping("/api/bpm/flows/{flowDefId}/versions/{versionId}/nodes") @RequiredArgsConstructor @Slf4j
public class NodeController {
    private final BpmFlowNodeService svc;
    private final BpmWfDebugLogMapper debugLogMapper;
    private static final ObjectMapper OM = new ObjectMapper();

    /** 节点生命周期日志。注意 wf_debug_log.context 是 JSON 列，必须写合法 JSON（裸词字符串会被 MySQL 拒绝） */
    private void lifeLog(String flowDefId, String nodeKey, String level, String message) {
        try {
            WfDebugLog l = new WfDebugLog();
            l.setWorkflowId(flowDefId); l.setNodeKey(nodeKey); l.setLevel(level);
            l.setMessage(message);
            l.setContext(OM.writeValueAsString(Map.of("kind", "node")));
            l.setCreatedAt(LocalDateTime.now());
            debugLogMapper.insert(l);
        } catch (Exception e) {
            log.error("节点日志写入失败 wf={} node={}: {}", flowDefId, nodeKey, e.getMessage());
        }
    }

    @GetMapping public ApiResponse<?> list(@PathVariable String flowDefId, @PathVariable String versionId) { return ApiResponse.success(svc.listByVersion(versionId)); }
    @GetMapping("/{nodeKey}") public ApiResponse<?> get(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey) { return ApiResponse.success(svc.getByKey(versionId, nodeKey)); }

    @PostMapping public ApiResponse<?> create(@PathVariable String flowDefId, @PathVariable String versionId, @RequestBody NodeRequest req) {
        var resp = ApiResponse.success(svc.create(versionId, req));
        lifeLog(flowDefId, req.getNodeKey(), "INFO", "创建节点「" + req.getName() + "」（类型：" + req.getNodeType() + "）");
        return resp;
    }

    @PutMapping("/{nodeKey}") public ApiResponse<?> update(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey, @RequestBody NodeRequest req) {
        // 变更检测：全量保存场景下仅在有实际变化（或新建）时写日志，避免刷屏
        var before = svc.getByKey(versionId, nodeKey);
        var resp = ApiResponse.success(svc.update(versionId, nodeKey, req));
        try {
            var after = svc.getByKey(versionId, nodeKey);
            if (after != null) {
                if (before == null) {
                    lifeLog(flowDefId, nodeKey, "INFO", "创建节点「" + after.getName() + "」（类型：" + after.getNodeType() + "）");
                } else {
                    List<String> changes = new ArrayList<>();
                    if (!Objects.equals(before.getName(), after.getName()))
                        changes.add("重命名「" + before.getName() + "」→「" + after.getName() + "」");
                    if (!Objects.equals(before.getNodeType(), after.getNodeType()))
                        changes.add("类型 " + before.getNodeType() + "→" + after.getNodeType());
                    if (!Objects.equals(normCfg(before.getConfig()), normCfg(after.getConfig())))
                        changes.add("修改节点配置");
                    if (!changes.isEmpty())
                        lifeLog(flowDefId, nodeKey, "INFO", "更新节点「" + after.getName() + "」：" + String.join("；", changes));
                }
            }
        } catch (Exception ignore) { }
        return resp;
    }

    @PutMapping("/{nodeKey}/position") public ApiResponse<?> move(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey, @RequestBody java.util.Map<String, Integer> body) { return ApiResponse.success(svc.move(versionId, nodeKey, body.getOrDefault("x", 0), body.getOrDefault("y", 0))); }

    @PutMapping("/{nodeKey}/config") public ApiResponse<?> config(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey, @RequestBody java.util.Map<String, Object> body) {
        Object c = body.get("config");
        String next = c == null ? null : c.toString();
        var before = svc.getByKey(versionId, nodeKey);
        var resp = ApiResponse.success(svc.updateConfig(versionId, nodeKey, next));
        try {
            var after = svc.getByKey(versionId, nodeKey);
            if (after != null && !Objects.equals(normCfg(before == null ? null : before.getConfig()), normCfg(after.getConfig())))
                lifeLog(flowDefId, nodeKey, "INFO", "更新节点「" + after.getName() + "」：修改节点配置");
        } catch (Exception ignore) { }
        return resp;
    }

    @DeleteMapping("/{nodeKey}") public ApiResponse<?> delete(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey) {
        var before = svc.getByKey(versionId, nodeKey);
        svc.delete(versionId, nodeKey);
        lifeLog(flowDefId, nodeKey, "WARN", "删除节点「" + (before != null ? before.getName() : nodeKey) + "」（" + nodeKey + "）及其连线");
        return ApiResponse.success();
    }

    /** 配置 JSON 归一化比较（键序/空白差异视为未变化）；解析失败按原文比较 */
    private String normCfg(String s) {
        if (s == null || s.isBlank()) return "";
        try { return OM.readTree(s).toString(); } catch (Exception e) { return s; }
    }
}
