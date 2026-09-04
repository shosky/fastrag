package com.fastrag.module.bpm.executor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.bpm.dto.InstanceTriggerRequest;
import com.fastrag.module.bpm.dto.InstanceVO;
import com.fastrag.module.bpm.entity.BpmFlowDef;
import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.entity.BpmFlowVersion;
import com.fastrag.module.bpm.enums.VersionStatus;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import com.fastrag.module.bpm.mapper.BpmFlowDefMapper;
import com.fastrag.module.bpm.mapper.BpmFlowVersionMapper;
import com.fastrag.module.bpm.service.BpmInstanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** SubflowNodeExecutor:校验 + 触发 + 同步等待 + outputMapping */
@ExtendWith(MockitoExtension.class)
class SubflowNodeExecutorTest {

    @Mock BpmInstanceService instanceService;
    @Mock BpmFlowDefMapper defMapper;
    @Mock BpmFlowVersionMapper versionMapper;

    private final SpelEvaluator spel = new SpelEvaluator();

    private SubflowNodeExecutor newEx() {
        return new SubflowNodeExecutor(spel, instanceService, defMapper, versionMapper);
    }

    private BpmFlowNode node(String config) {
        BpmFlowNode n = new BpmFlowNode();
        n.setNodeType("subflow");
        n.setNodeKey("s1");
        n.setConfig(config);
        return n;
    }

    private ExecutionContext ctx(BpmFlowNode n, int depth) {
        ExecutionContext c = new ExecutionContext();
        c.setFlowDefId("parent");
        c.setCurrentNode(n);
        c.setVariables(new HashMap<>());
        c.setSubflowDepth(depth);
        c.setOperatorId("op1");
        return c;
    }

    private BpmFlowDef def(String id, String currentVerId) {
        BpmFlowDef d = new BpmFlowDef();
        d.setId(id);
        d.setCurrentVersionId(currentVerId);
        d.setName("子流程 " + id);
        return d;
    }

    private BpmFlowVersion ver(String id, int no, String status) {
        BpmFlowVersion v = new BpmFlowVersion();
        v.setId(id);
        v.setFlowDefId("sub1");
        v.setVersionNo(no);
        v.setStatus(status);
        return v;
    }

    private InstanceVO vo(String status) {
        InstanceVO v = new InstanceVO();
        v.setId("subInst1");
        v.setStatus(status);
        v.setOutputs(new HashMap<>());
        return v;
    }

    @Test
    @DisplayName("validateConfig:subFlowDefId 缺失抛 40021")
    void validateMissingDefId() {
        SubflowNodeExecutor ex = newEx();
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.validateConfig(Map.of()));
        assertEquals(40021, be.getCode());
    }

    @Test
    @DisplayName("子流程 def 不存在抛 SUBFLOW_NOT_FOUND(40060)")
    void defNotFound() {
        when(defMapper.selectById("missing")).thenReturn(null);
        SubflowNodeExecutor ex = newEx();
        String cfg = "{\"subFlowDefId\":\"'missing'\"}";
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.execute(ctx(node(cfg), 0)));
        assertEquals(40060, be.getCode());
    }

    @Test
    @DisplayName("当前版本未发布抛 SUBFLOW_NOT_FOUND(40060)")
    void notPublished() {
        when(defMapper.selectById("sub1")).thenReturn(def("sub1", "verX"));
        when(versionMapper.selectById("verX")).thenReturn(ver("verX", 1, VersionStatus.draft.name()));
        SubflowNodeExecutor ex = newEx();
        String cfg = "{\"subFlowDefId\":\"'sub1'\"}";
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.execute(ctx(node(cfg), 0)));
        assertEquals(40060, be.getCode());
    }

    @Test
    @DisplayName("subflowDepth>=5 抛 SUBFLOW_DEPTH_EXCEEDED(40012)")
    void depthExceeded() {
        SubflowNodeExecutor ex = newEx();
        String cfg = "{\"subFlowDefId\":\"'sub1'\"}";
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.execute(ctx(node(cfg), 5)));
        assertEquals(40012, be.getCode());
    }

    @Test
    @DisplayName("同步 completed:trigger + 等待 + outputMapping")
    void syncCompleted() {
        when(defMapper.selectById("sub1")).thenReturn(def("sub1", "verP"));
        when(versionMapper.selectById("verP")).thenReturn(ver("verP", 2, VersionStatus.published.name()));
        when(instanceService.trigger(any(InstanceTriggerRequest.class))).thenReturn("subInst1");
        InstanceVO v1 = vo("running");
        v1.getOutputs().put("sum", 100);
        InstanceVO v2 = vo("completed");
        v2.getOutputs().put("sum", 100);
        when(instanceService.detail("subInst1")).thenReturn(v1, v2);

        SubflowNodeExecutor ex = newEx();
        String cfg = "{\"subFlowDefId\":\"'sub1'\",\"inputMapping\":{\"k\":\"'42'\"},\"outputMapping\":{\"x\":\"'sum=' + #sum\"},\"waitForCompletion\":true,\"timeoutMs\":5000}";
        NodeExecutionResult r = ex.execute(ctx(node(cfg), 0));
        assertEquals("subInst1", r.getOutputs().get("subInstanceId"));
        assertEquals("completed", r.getOutputs().get("status"));
        assertEquals("sum=100", r.getOutputs().get("x"));

        // 验证 trigger 收到的 inputParams
        ArgumentCaptor<InstanceTriggerRequest> cap = ArgumentCaptor.forClass(InstanceTriggerRequest.class);
        verify(instanceService).trigger(cap.capture());
        assertEquals("42", cap.getValue().getInputParams().get("k"));
        assertEquals("subflow", cap.getValue().getTriggerType());
        assertEquals("op1", cap.getValue().getStartUserId());
    }

    @Test
    @DisplayName("同步 failed 抛 EXECUTION_FAILED(40041)")
    void syncFailed() {
        when(defMapper.selectById("sub1")).thenReturn(def("sub1", "verP"));
        when(versionMapper.selectById("verP")).thenReturn(ver("verP", 1, VersionStatus.published.name()));
        when(instanceService.trigger(any(InstanceTriggerRequest.class))).thenReturn("subInst1");
        InstanceVO v = vo("failed");
        v.setFailureReason("child error");
        when(instanceService.detail("subInst1")).thenReturn(v);

        SubflowNodeExecutor ex = newEx();
        String cfg = "{\"subFlowDefId\":\"'sub1'\",\"waitForCompletion\":true}";
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.execute(ctx(node(cfg), 0)));
        assertEquals(40041, be.getCode());
        assertTrue(be.getMessage().contains("child error"));
    }

    @Test
    @DisplayName("异步模式:立即返回 status=pending,不轮询")
    void asyncMode() {
        when(defMapper.selectById("sub1")).thenReturn(def("sub1", "verP"));
        when(versionMapper.selectById("verP")).thenReturn(ver("verP", 1, VersionStatus.published.name()));
        when(instanceService.trigger(any(InstanceTriggerRequest.class))).thenReturn("subInst1");

        SubflowNodeExecutor ex = newEx();
        String cfg = "{\"subFlowDefId\":\"'sub1'\",\"waitForCompletion\":false}";
        NodeExecutionResult r = ex.execute(ctx(node(cfg), 0));
        assertEquals("subInst1", r.getOutputs().get("subInstanceId"));
        assertEquals("pending", r.getOutputs().get("status"));
        verify(instanceService, never()).detail(anyString());
    }

    @Test
    @DisplayName("指定 versionNo 时按该版本查找")
    void explicitVersionNo() {
        when(defMapper.selectById("sub1")).thenReturn(def("sub1", "verP"));
        when(versionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ver("ver2", 2, VersionStatus.published.name()));
        when(instanceService.trigger(any(InstanceTriggerRequest.class))).thenReturn("subInst1");
        InstanceVO v = vo("completed");
        v.getOutputs().put("k", "v");
        when(instanceService.detail("subInst1")).thenReturn(v);

        SubflowNodeExecutor ex = newEx();
        String cfg = "{\"subFlowDefId\":\"'sub1'\",\"versionNo\":2,\"waitForCompletion\":true}";
        NodeExecutionResult r = ex.execute(ctx(node(cfg), 0));
        assertEquals(2, r.getOutputs().get("subVersionNo"));
        verify(versionMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(versionMapper, never()).selectById(anyString());
    }
}