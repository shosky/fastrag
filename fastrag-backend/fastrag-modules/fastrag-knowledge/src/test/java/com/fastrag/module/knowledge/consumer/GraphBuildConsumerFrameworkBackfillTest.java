package com.fastrag.module.knowledge.consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨分片框架关系兜底（方案A）纯逻辑测试：headingPath 末级标题提取与框架类型门控。
 * Neo4j 查询部分依赖真实图库，不在单测范围。
 */
class GraphBuildConsumerFrameworkBackfillTest {

    @Test
    void frameworkTitleOf_takesLastNonBlankSegment() {
        assertEquals("营销六步法", GraphBuildConsumer.frameworkTitleOf("小微ICT业务 > 营销六步法"));
        assertEquals("营销六步法", GraphBuildConsumer.frameworkTitleOf("第一部分 > 小微ICT业务 > 营销六步法"));
        assertEquals("单级标题", GraphBuildConsumer.frameworkTitleOf("单级标题"));
        // 空段跳过
        assertEquals("营销六步法", GraphBuildConsumer.frameworkTitleOf(" > > 营销六步法 > "));
    }

    @Test
    void frameworkTitleOf_blankReturnsNull() {
        assertNull(GraphBuildConsumer.frameworkTitleOf(null));
        assertNull(GraphBuildConsumer.frameworkTitleOf(""));
        assertNull(GraphBuildConsumer.frameworkTitleOf("   "));
        assertNull(GraphBuildConsumer.frameworkTitleOf(" > > "));
    }

    @Test
    void isFrameworkType_gatesToMethodologyTypes() {
        assertTrue(GraphBuildConsumer.isFrameworkType("方法"));
        assertTrue(GraphBuildConsumer.isFrameworkType("业务流程"));
        assertTrue(GraphBuildConsumer.isFrameworkType("流程"));
        assertTrue(GraphBuildConsumer.isFrameworkType("步骤"));
        assertTrue(GraphBuildConsumer.isFrameworkType("方案"));
        // 宽泛类型不触发（避免普通章节成员误连）
        assertFalse(GraphBuildConsumer.isFrameworkType("业务"));
        assertFalse(GraphBuildConsumer.isFrameworkType("场景"));
        // 占位与空类型不触发
        assertFalse(GraphBuildConsumer.isFrameworkType("UNKNOWN"));
        assertFalse(GraphBuildConsumer.isFrameworkType(null));
    }
}
