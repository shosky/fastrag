package com.fastrag.module.knowledge.config;

import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.platform.service.ConfigManageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 解析策略配置合并解析器单元测试。
 *
 * 覆盖：新增分片策略字段（strategy/parentMaxChunkLength/parentAggLevel/semanticThreshold/embeddingModel）
 * 从 advanced JSON 正确映射进 ChunkConfig；未配置时使用代码默认值。
 */
@ExtendWith(MockitoExtension.class)
class StrategyConfigResolverTest {

    @Mock
    private KbParseStrategyMapper strategyMapper;

    @Mock
    private ConfigManageService configService;

    private StrategyConfigResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StrategyConfigResolver(strategyMapper, configService);
    }

    private static KbParseStrategy strategy(String advanced) {
        KbParseStrategy s = new KbParseStrategy();
        s.setId("s1");
        s.setAdvanced(advanced);
        return s;
    }

    @Test
    void 新分片策略字段正确映射() {
        when(strategyMapper.selectById("s1")).thenReturn(strategy(
                "{\"chunk\":{\"strategy\":\"parent_child\",\"parentMaxChunkLength\":3000," +
                        "\"parentAggLevel\":\"H2\",\"semanticThreshold\":45,\"embeddingModel\":\"embed-2\"}}"));

        ParseStrategyConfig config = resolver.resolve("s1");

        assertEquals("parent_child", config.getChunk().getStrategy());
        assertEquals(3000, config.getChunk().getParentMaxChunkLength());
        assertEquals("H2", config.getChunk().getParentAggLevel());
        assertEquals(45, config.getChunk().getSemanticThreshold());
        assertEquals("embed-2", config.getChunk().getEmbeddingModel());
    }

    @Test
    void 未配置时使用代码默认值() {
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"chunk\":{\"chunkLength\":2000}}"));

        ParseStrategyConfig config = resolver.resolve("s1");

        assertEquals("rule_fixed", config.getChunk().getStrategy(), "默认策略为 rule_fixed");
        assertEquals(2000, config.getChunk().getParentMaxChunkLength(), "父分片默认最大长度 2000");
        assertEquals("auto", config.getChunk().getParentAggLevel());
        assertEquals(30, config.getChunk().getSemanticThreshold());
        assertEquals("", config.getChunk().getEmbeddingModel());
    }

    @Test
    void 策略ID为空时仅使用系统默认值() {
        ParseStrategyConfig config = resolver.resolve((String) null);

        assertEquals("rule_fixed", config.getChunk().getStrategy());
        assertEquals(1000, config.getChunk().getChunkLength());
    }
}
