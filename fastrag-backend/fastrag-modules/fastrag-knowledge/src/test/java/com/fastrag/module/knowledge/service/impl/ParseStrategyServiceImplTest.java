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

    // ==================== excel_row 按行切片保存校验（REQ-H02/H03/H04，interfaces.md API-013~015） ====================

    /** 构造 excel_row 保存请求：默认合法三参（表头 1~2 行、切分起始 3、行数 4） */
    private static ParseStrategyRequest excelReq(List<String> extensions, Map<String, Object> excelRow) {
        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setName("Excel 按行切片");
        req.setParseMethod("xlsx");
        req.setExtensions(extensions);
        Map<String, Object> chunk = new java.util.HashMap<>();
        chunk.put("strategy", "excel_row");
        if (excelRow != null) chunk.put("excelRow", excelRow);
        req.setAdvanced(Map.of("chunk", chunk));
        return req;
    }

    private static Map<String, Object> excelRow(int start, int end, int sliceStart, Integer rows) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("headerStartRow", start);
        m.put("headerEndRow", end);
        m.put("sliceStartRow", sliceStart);
        if (rows != null) m.put("rowsPerSlice", rows);
        return m;
    }

    @Test
    void create拒绝excel_row携带非Excel扩展名() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        // parseMethod=default（兼容全部扩展名，绕过 REQ-G04 兼容校验）→ 命中 excel_row 域校验（ERR-PS-EXT-DOMAIN）
        ParseStrategyRequest req = excelReq(List.of(".xlsx", ".pdf"), excelRow(1, 2, 3, 4));
        req.setParseMethod("default");

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.create("kb_1", req));
        assertTrue(ex.getMessage().contains("按行切片策略仅支持 Excel 扩展名"));
    }

    @Test
    void create拒绝excel_row表头起始大于结束() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create("kb_1", excelReq(List.of(".xlsx"), excelRow(3, 2, 4, 4))));
        assertTrue(ex.getMessage().contains("表头起始行"));
    }

    @Test
    void create拒绝excel_row表头区间超过5行() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create("kb_1", excelReq(List.of(".xlsx"), excelRow(1, 6, 7, 4))));
        assertTrue(ex.getMessage().contains("表头区间不可超过 5 行"));
    }

    @Test
    void create通过表头区间恰5行() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(KbParseStrategy.class))).thenReturn(1);

        assertDoesNotThrow(() -> svc.create("kb_1", excelReq(List.of(".xlsx"), excelRow(1, 5, 6, 4))));
    }

    @Test
    void create拒绝切分起始行不大于表头结束行() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create("kb_1", excelReq(List.of(".xlsx"), excelRow(1, 2, 2, 4))));
        assertTrue(ex.getMessage().contains("切分起始行"));
    }

    @Test
    void create通过切分起始行为表头结束加一() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(KbParseStrategy.class))).thenReturn(1);

        assertDoesNotThrow(() -> svc.create("kb_1", excelReq(List.of(".xlsx"), excelRow(1, 2, 3, 4))));
    }

    @Test
    void create拒绝rowsPerSlice小于1() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create("kb_1", excelReq(List.of(".xlsx"), excelRow(1, 2, 3, 0))));
        assertTrue(ex.getMessage().contains("切分行数必须不小于 1"));
    }

    @Test
    void create通过超大rowsPerSlice由字符硬限兜底() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(KbParseStrategy.class))).thenReturn(1);

        assertDoesNotThrow(() -> svc.create("kb_1", excelReq(List.of(".xlsx"), excelRow(1, 2, 3, 10000))));
    }

    @Test
    void create非excel_row策略携带残缺excelRow时放行() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(KbParseStrategy.class))).thenReturn(1);

        // strategy=rule_fixed：excelRow 组不被消费，残留无副作用、不校验（REQ-H01）
        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setName("规则分片");
        req.setParseMethod("default");
        req.setExtensions(List.of(".md"));
        req.setAdvanced(Map.of("chunk", Map.of(
                "strategy", "rule_fixed",
                "excelRow", excelRow(9, 1, 0, 0))));

        assertDoesNotThrow(() -> svc.create("kb_1", req));
    }

    @Test
    void create拒绝excel_row缺失三参() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create("kb_1", excelReq(List.of(".xlsx"), null)));
        assertTrue(ex.getMessage().contains("必须配置表头起始行"));
    }

    @Test
    void update按生效扩展名校验excel_row() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        // 库内策略 extensions=[".pdf"]；请求仅带 advanced（strategy=excel_row）不带 extensions →
        // 生效扩展名沿用库内 ".pdf" → 拒绝（REQ-A04 生效值口径）
        KbParseStrategy existing = new KbParseStrategy();
        existing.setId("strategy_001");
        existing.setKbId("kb_1");
        existing.setName("旧策略");
        existing.setParseMethod("xlsx");
        existing.setExtensions("[\".pdf\"]");
        existing.setIsDefault(0);
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(existing);

        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setAdvanced(Map.of("chunk", Map.of("strategy", "excel_row",
                "excelRow", excelRow(1, 2, 3, 4))));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.update("kb_1", "strategy_001", req));
        assertTrue(ex.getMessage().contains("按行切片策略仅支持 Excel 扩展名"));
    }

    @Test
    void update仅改name时excel_row生效值放行() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        KbParseStrategy existing = new KbParseStrategy();
        existing.setId("strategy_001");
        existing.setKbId("kb_1");
        existing.setName("旧策略");
        existing.setParseMethod("xlsx");
        existing.setExtensions("[\".xlsx\"]");
        existing.setIsDefault(0);
        existing.setAdvanced("{\"chunk\":{\"strategy\":\"excel_row\",\"excelRow\":{\"headerStartRow\":1,\"headerEndRow\":2,\"sliceStartRow\":3,\"rowsPerSlice\":4}}}");
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(existing);

        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setName("改名");

        assertDoesNotThrow(() -> svc.update("kb_1", "strategy_001", req));
        verify(mapper).updateById(org.mockito.ArgumentMatchers.any(KbParseStrategy.class));
    }

    @Test
    void 文件级保存excel_row同族校验() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        // parseMethod=default（兼容 .csv）→ 命中 excel_row 域校验
        ParseStrategyRequest req = excelReq(List.of(".csv"), excelRow(1, 2, 3, 4));
        req.setParseMethod("default");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.saveFileStrategy("kb_1", "file_1", req));
        assertTrue(ex.getMessage().contains("按行切片策略仅支持 Excel 扩展名"));
    }

    @Test
    void excelRow参数仅新分组承载() {
        ParseStrategyServiceImpl svc = new ParseStrategyServiceImpl(mapper, fileMapper);

        // 旧平铺格式（无 chunk 分组）出现 strategy=excel_row → 三参缺失即拒绝（excelRow 组仅新分组承载，无平铺形态）
        ParseStrategyRequest req = new ParseStrategyRequest();
        req.setName("平铺 excel_row");
        req.setParseMethod("xlsx");
        req.setExtensions(List.of(".xlsx"));
        req.setAdvanced(Map.of(
                "strategy", "excel_row",
                "headerStartRow", 1,
                "headerEndRow", 2,
                "sliceStartRow", 3));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.create("kb_1", req));
        assertTrue(ex.getMessage().contains("必须配置表头起始行"));
    }
}
