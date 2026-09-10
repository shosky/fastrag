package com.fastrag.module.graph.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 实体类型归一化测试：白名单精确命中 / 编辑距离归并 / 未命中归 UNKNOWN
 */
class EntityTypeNormalizerTest {

    @Test
    void normalize_exactWhitelistHit_keepsType() {
        assertEquals("故障类型", EntityTypeNormalizer.normalize("故障类型"));
        assertEquals("根因", EntityTypeNormalizer.normalize("根因"));
        assertEquals("Entity", EntityTypeNormalizer.normalize("Entity"));
        assertEquals("故障类型", EntityTypeNormalizer.normalize("故障类型 ")); // 去空白后精确
    }

    @Test
    void normalize_editDistanceOne_mergedToWhitelist() {
        // "故障类别" 与 "故障类型" 编辑距离 1 → 归并
        assertEquals("故障类型", EntityTypeNormalizer.normalize("故障类别"));
        // "监控项" 与 "监控项" 距离 0（精确）；"监控项目" 与 "监控项" 距离 1 → 归并
        assertEquals("监控项", EntityTypeNormalizer.normalize("监控项目"));
    }

    @Test
    void normalize_unknownType_fallsBackToUnknown() {
        assertEquals(EntityTypeNormalizer.UNKNOWN, EntityTypeNormalizer.normalize("随便乱造的类型XYZ"));
        assertEquals(EntityTypeNormalizer.UNKNOWN, EntityTypeNormalizer.normalize(""));
        assertEquals(EntityTypeNormalizer.UNKNOWN, EntityTypeNormalizer.normalize(null));
        assertEquals(EntityTypeNormalizer.UNKNOWN, EntityTypeNormalizer.normalize("   "));
    }

    @Test
    void normalize_customWhitelist_usedInsteadOfDefault() {
        List<String> custom = List.of("合同", "条款");
        assertEquals("合同", EntityTypeNormalizer.normalize("合同", custom));
        assertEquals("条款", EntityTypeNormalizer.normalize("条款", custom));
        // 自定义白名单下，"故障类型" 不在其中 → UNKNOWN
        assertEquals(EntityTypeNormalizer.UNKNOWN, EntityTypeNormalizer.normalize("故障类型", custom));
    }

    @Test
    void normalize_telecomProductDomainTypes_hitWhitelist() {
        // 电信/产品营销域类型（生产数据中曾因白名单缺失大量落 UNKNOWN）
        assertEquals("套餐", EntityTypeNormalizer.normalize("套餐"));
        assertEquals("产品", EntityTypeNormalizer.normalize("产品"));
        assertEquals("客户", EntityTypeNormalizer.normalize("客户"));
        assertEquals("补贴", EntityTypeNormalizer.normalize("补贴"));
        assertEquals("报价单", EntityTypeNormalizer.normalize("报价单"));
        assertEquals("维保", EntityTypeNormalizer.normalize("维保 "));
    }
}
