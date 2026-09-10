package com.fastrag.module.knowledge.service.impl;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.model.ParseStrategyDto;
import com.fastrag.module.knowledge.model.ParseStrategyRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解析策略保存接口单元测试。
 *
 * 验证：advanced（含新分片策略字段）作为自由 JSON 透传保存，创建后读回 DTO 字段完整。
 * 后端 advanced 为 String 列，无需 DDL 变更即可持久化新字段。
 */
@ExtendWith(MockitoExtension.class)
class ParseStrategyServiceImplTest {

    @Mock
    private KbParseStrategyMapper mapper;

    @Mock
    private KbFileMapper fileMapper;

    @Test
    void create保存advanced含分片策略字段并可读回() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setName("父子切片策略");
        req.setDescription("按标题聚合父分片");
        req.setParseMethod("default");
        req.setExtensions(List.of(".md", ".txt"));
        req.setAdvanced(Map.of(
                "chunk", Map.of(
                        "strategy", "parent_child",
                        "chunkLength", 800,
                        "overlap", 50,
                        "parentMaxChunkLength", 2400,
                        "parentAggLevel", "H2",
                        "semanticThreshold", 30,
                        "embeddingModel", "")));

        // insert 后模拟实体携带生成 ID（MyBatis-Plus ASSIGN_ID 回填）
        ArgumentCaptor<KbParseStrategy> captor = ArgumentCaptor.forClass(KbParseStrategy.class);
        when(mapper.insert(captor.capture())).thenAnswer(inv -> {
            KbParseStrategy e = captor.getValue();
            e.setId("strategy_001");
            return 1;
        });

        ParseStrategyDto dto = svc.create("kb_1", req);

        // 1. 落库的 advanced JSON 含新字段（透传保存验证）
        KbParseStrategy saved = captor.getValue();
        assertTrue(saved.getAdvanced().contains("\"strategy\":\"parent_child\""), "advanced JSON 应含 strategy 字段");
        assertTrue(saved.getAdvanced().contains("\"parentMaxChunkLength\":2400"));
        assertTrue(saved.getAdvanced().contains("\"parentAggLevel\":\"H2\""));

        // 2. 读回 DTO：advanced 完整保留
        assertNotNull(dto.getAdvanced());
        Map<?, ?> chunk = (Map<?, ?>) dto.getAdvanced().get("chunk");
        assertEquals("parent_child", chunk.get("strategy"));
        assertEquals(800, ((Number) chunk.get("chunkLength")).intValue());
        assertEquals(2400, ((Number) chunk.get("parentMaxChunkLength")).intValue());
        assertEquals("H2", chunk.get("parentAggLevel"));
        assertEquals(30, ((Number) chunk.get("semanticThreshold")).intValue());
    }

    @Test
    void update替换advanced为新字段配置() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        // 已存在的旧策略（无 strategy 字段）
        KbParseStrategy existing = new KbParseStrategy();
        existing.setId("strategy_001");
        existing.setKbId("kb_1");
        existing.setName("旧策略");
        existing.setParseMethod("default");
        existing.setIsDefault(0);
        existing.setAdvanced("{\"chunk\":{\"chunkLength\":1000}}");
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(existing);

        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setAdvanced(Map.of(
                "chunk", Map.of(
                        "strategy", "semantic",
                        "chunkLength", 600,
                        "semanticThreshold", 40,
                        "embeddingModel", "embed-9")));

        svc.update("kb_1", "strategy_001", req);

        ArgumentCaptor<KbParseStrategy> captor = ArgumentCaptor.forClass(KbParseStrategy.class);
        verify(mapper).updateById(captor.capture());
        String advanced = captor.getValue().getAdvanced();
        assertTrue(advanced.contains("\"strategy\":\"semantic\""), "update 应整体替换 advanced 为新字段配置");
        assertTrue(advanced.contains("\"semanticThreshold\":40"));
        assertTrue(advanced.contains("\"embeddingModel\":\"embed-9\""));
    }

    @Test
    void create拒绝空名称() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setName(" ");
        req.setDescription("描述");
        req.setParseMethod("default");
        req.setExtensions(List.of(".md"));

        assertThrows(BusinessException.class, () -> svc.create("kb_1", req));
    }

    @Test
    void create拒绝缺失扩展名字段() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setName("策略");
        req.setDescription("描述");
        req.setParseMethod("default");
        req.setExtensions(null);

        assertThrows(BusinessException.class, () -> svc.create("kb_1", req));
    }

    @Test
    void create拒绝空扩展名列表() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setName("策略");
        req.setDescription("描述");
        req.setParseMethod("default");
        req.setExtensions(List.of());

        assertThrows(BusinessException.class, () -> svc.create("kb_1", req));
    }

    @Test
    void update拒绝提供空名称() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        KbParseStrategy existing = new KbParseStrategy();
        existing.setId("strategy_001");
        existing.setKbId("kb_1");
        existing.setName("旧策略");
        existing.setParseMethod("default");
        existing.setIsDefault(0);
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(existing);

        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setName("");

        assertThrows(BusinessException.class, () -> svc.update("kb_1", "strategy_001", req));
    }

    @Test
    void update拒绝提供空扩展名列表() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        KbParseStrategy existing = new KbParseStrategy();
        existing.setId("strategy_001");
        existing.setKbId("kb_1");
        existing.setName("旧策略");
        existing.setParseMethod("default");
        existing.setIsDefault(0);
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(existing);

        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setExtensions(List.of());

        assertThrows(BusinessException.class, () -> svc.update("kb_1", "strategy_001", req));
    }
}
