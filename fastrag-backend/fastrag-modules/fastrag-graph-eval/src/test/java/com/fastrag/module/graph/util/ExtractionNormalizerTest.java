package com.fastrag.module.graph.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 抽取结果规范化测试：同名实体去重合并、description 择长（gleaning 补捞合并语义）。
 */
class ExtractionNormalizerTest {

    @Test
    void normalize_mergesDuplicateEntities_attributesUnion() {
        ExtractionNormalizer.Entity a = entity("华为B671-S2", "设备", "从光猫型号", "价格", "619");
        ExtractionNormalizer.Entity b = entity("华为B671-S2", "设备", "从光猫型号", "品牌", "华为");

        ExtractionNormalizer.ExtractionResult merged = normalize(a, b, List.of());

        assertEquals(1, merged.getEntities().size());
        assertEquals(2, merged.getEntities().get(0).getAttributes().size());
    }

    @Test
    void normalize_duplicateEntity_keepsLongerDescription() {
        ExtractionNormalizer.Entity shortDesc = entity("海康", "品牌", "摄像头品牌", null, null);
        ExtractionNormalizer.Entity longDesc = entity("海康", "品牌", "海康威视，全球领先的视频监控设备厂商", null, null);

        ExtractionNormalizer.ExtractionResult merged = normalize(shortDesc, longDesc, List.of());
        assertEquals("海康威视，全球领先的视频监控设备厂商", merged.getEntities().get(0).getDescription());

        // 顺序反转结果一致（择长与出现顺序无关）
        ExtractionNormalizer.ExtractionResult mergedReversed = normalize(longDesc, shortDesc, List.of());
        assertEquals("海康威视，全球领先的视频监控设备厂商", mergedReversed.getEntities().get(0).getDescription());
    }

    @Test
    void normalize_cjkSpaceVariants_mergeToSameEntity() {
        // "小微 ICT" 与 "小微ICT" 必须合并（LightRAG 空格归一规则）
        ExtractionNormalizer.Entity spaced = entity("小微 ICT", "业务", null, null, null);
        ExtractionNormalizer.Entity tight = entity("小微ICT", "业务", "面向小微企业的ICT业务", null, null);

        ExtractionNormalizer.ExtractionResult merged = normalize(spaced, tight, List.of());
        assertEquals(1, merged.getEntities().size());
        assertEquals("面向小微企业的ICT业务", merged.getEntities().get(0).getDescription());
    }

    @Test
    void normalize_relationEndpoints_resolvedToCanonicalText() {
        ExtractionNormalizer.Entity pkg = entity("4个摄像头标准包", "套餐", null, null, null);
        ExtractionNormalizer.Entity cam = entity("摄像头", "设备", null, null, null);
        ExtractionNormalizer.Relation rel = new ExtractionNormalizer.Relation();
        rel.setSource("4个摄像头标准包");
        rel.setTarget("摄像头");
        rel.setLabel("包含");

        ExtractionNormalizer.ExtractionResult merged = normalize(pkg, cam, List.of(rel));
        assertEquals("4个摄像头标准包", merged.getRelations().get(0).getSource());
        assertEquals("摄像头", merged.getRelations().get(0).getTarget());
    }

    @Test
    void normalize_blankDescriptions_filledByLaterDuplicate() {
        ExtractionNormalizer.Entity noDesc = entity("天翼云眼", "服务", null, null, null);
        ExtractionNormalizer.Entity withDesc = entity("天翼云眼", "服务", "视频流集约云服务", null, null);

        ExtractionNormalizer.ExtractionResult merged = normalize(noDesc, withDesc, List.of());
        assertEquals("视频流集约云服务", merged.getEntities().get(0).getDescription());
        assertTrue(merged.getEntities().get(0).getDescription().length() > 0);
    }

    // ==================== 辅助 ====================

    private ExtractionNormalizer.Entity entity(String text, String label, String desc, String attrText, String attrLabel) {
        ExtractionNormalizer.Entity e = new ExtractionNormalizer.Entity();
        e.setText(text);
        e.setLabel(label);
        e.setDescription(desc);
        if (attrText != null) {
            ExtractionNormalizer.Attribute a = new ExtractionNormalizer.Attribute();
            a.setText(attrText);
            a.setLabel(attrLabel);
            e.setAttributes(new ArrayList<>(List.of(a)));
        }
        return e;
    }

    private ExtractionNormalizer.ExtractionResult normalize(
            ExtractionNormalizer.Entity e1, ExtractionNormalizer.Entity e2,
            List<ExtractionNormalizer.Relation> relations) {
        ExtractionNormalizer.ExtractionResult result = new ExtractionNormalizer.ExtractionResult();
        result.setEntities(new ArrayList<>(List.of(e1, e2)));
        result.setRelations(new ArrayList<>(relations));
        return ExtractionNormalizer.normalize(result);
    }
}
