package com.fastrag.module.knowledge.consumer;

import com.fastrag.module.graph.util.EntityTypeNormalizer;
import com.fastrag.module.graph.util.ExtractionNormalizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图谱抽取 Prompt 组装回归测试。
 *
 * <p>背景：2026-09-07 生产事故——模板改用 String.formatted 后残留了一个无参的 %s 占位符，
 * 全部 chunk 抽取抛 MissingFormatArgumentException（64/64 failed）。本测试锁死：
 * prompt 组装永远纯拼接、可容忍文本中的 % 字符、分支内容正确。</p>
 */
class GraphBuildConsumerPromptTest {

    private static final String BOM_TABLE = """
            | 费用类型 | 型号 | 价格 |
            | --- | --- | --- |
            | 设备-主光猫 | 华为B866-S2-5E4P5W1 | 1078 |
            | 设备-从光猫 | 华为B671-S2 | 619 |
            | 工费 | 安装及调测 | 260 |
            """;

    @Test
    void build_normalText_containsCoreRulesAndWhitelist() {
        String prompt = GraphBuildConsumer.buildExtractionPromptText(
                "小微ICT业务包含全光组网、视频监控等场景。", null, null, EntityTypeNormalizer.DEFAULT_WHITELIST);

        assertTrue(prompt.contains("从文本中提取实体和实体间的关系"));
        assertTrue(prompt.contains("小微ICT业务"));
        // 白名单类型提示存在，且值型类型被排除（"阈值"仅出现在白名单排除集中，
        // 模板规则文本不含该词，可作为类型提示排除性的探针）
        assertTrue(prompt.contains("套餐"));
        assertFalse(prompt.contains("阈值"));
        // 非表格文本应带流程/方法论抽取规则 + 章节标题框架实体规则（跨 chunk 框架关系，2026-09-07）
        assertTrue(prompt.contains("六步法"));
        assertTrue(prompt.contains("章节标题"));
        assertTrue(prompt.contains("属于"));
        // 不应带表格专用规则
        assertFalse(prompt.contains("价目表格"));
    }

    @Test
    void build_tableDominantText_switchesToTableRules() {
        String prompt = GraphBuildConsumer.buildExtractionPromptText(
                BOM_TABLE, null, null, EntityTypeNormalizer.DEFAULT_WHITELIST);

        assertTrue(prompt.contains("价目表格"));
        assertTrue(prompt.contains("套餐→设备"));
        // 表格模式不含普通流程规则，避免指令冲突
        assertFalse(prompt.contains("六步法"));
    }

    @Test
    void build_headingPathInjectedAsContext() {
        String prompt = GraphBuildConsumer.buildExtractionPromptText(
                "一备：做好准备。二问：黄金四问。", "小微ICT业务营销六步法", "营销六步法",
                EntityTypeNormalizer.DEFAULT_WHITELIST);

        assertTrue(prompt.contains("本文本是《小微ICT业务营销六步法》章节的内容"));
        assertTrue(prompt.contains("最近标题：营销六步法"));
    }

    @Test
    void build_percentSignInText_neverThrowsAndStaysIntact() {
        // 事故场景回归：文档文本含 % 时（利润率、补贴比例），纯拼接必须原样保留且不抛异常
        String text = "利润率≧70%系数=2；按原合同金额5%签订维保续费1年合同；补贴金额为套餐费的30%。";
        String prompt = GraphBuildConsumer.buildExtractionPromptText(
                text, null, null, EntityTypeNormalizer.DEFAULT_WHITELIST);

        assertTrue(prompt.contains("利润率≧70%系数=2"));
        assertTrue(prompt.contains("30%"));
        assertFalse(prompt.contains("%s"));
    }

    @Test
    void build_longText_truncatedTo3000Chars() {
        String longText = "全光组网".repeat(2000);
        String prompt = GraphBuildConsumer.buildExtractionPromptText(
                longText, null, null, EntityTypeNormalizer.DEFAULT_WHITELIST);

        // 3000 字符截断（"全光组网"4 字符 × 750 = 3000）+ prompt 固定部分；文本部分不应全量进入
        int occurrences = prompt.split("全光组网", -1).length - 1;
        assertEquals(750, occurrences);
    }

    @Test
    void build_noStrayFormatSpecifierEver() {
        // 事故根因兜底：模板不得残留裸 %s 占位符（文本本身含 %s 时拼入 prompt 属预期，
        // 故输入只覆盖含 % 但不含 %s 的场景）
        for (String text : new String[]{BOM_TABLE, "小微ICT%", "利润率70%以上", "100%纯文本"}) {
            String prompt = GraphBuildConsumer.buildExtractionPromptText(
                    text, "章节>A", "A", EntityTypeNormalizer.DEFAULT_WHITELIST);
            assertFalse(prompt.contains("%s"), "prompt 含残留 %s 占位符, input=" + text);
        }
    }

    // ==================== Gleaning 补捞 ====================

    @Test
    void shouldGlean_onlyWhenEntitiesWithoutRelations() {
        assertTrue(GraphBuildConsumer.shouldGlean(result(
                new ExtractionNormalizer.Entity(), null)));
        // 有关系不补捞
        assertFalse(GraphBuildConsumer.shouldGlean(result(
                new ExtractionNormalizer.Entity(), new ExtractionNormalizer.Relation())));
        // 无实体不补捞
        assertFalse(GraphBuildConsumer.shouldGlean(result(null, null)));
        assertFalse(GraphBuildConsumer.shouldGlean(null));
    }

    @Test
    void buildGleaningPrompt_containsFirstRoundResultAndRules() {
        String prompt = GraphBuildConsumer.buildGleaningPrompt(
                "支持海康、大华等一线品牌接入。", "{\"entities\":[{\"text\":\"海康\"}],\"relations\":[]}");
        assertTrue(prompt.contains("第一轮抽取结果"));
        assertTrue(prompt.contains("\"text\":\"海康\""));
        assertTrue(prompt.contains("原文："));
        assertTrue(prompt.contains("支持海康、大华等一线品牌接入。"));
        assertFalse(prompt.contains("%s"));
    }

    @Test
    void mergeExtractionResults_dedupesAndKeepsLongerDescription() {
        ExtractionNormalizer.Entity haiKang1 = new ExtractionNormalizer.Entity();
        haiKang1.setText("海康");
        haiKang1.setLabel("品牌");
        haiKang1.setDescription("摄像头品牌");

        ExtractionNormalizer.Entity daHua = new ExtractionNormalizer.Entity();
        daHua.setText("大华");
        daHua.setLabel("品牌");
        daHua.setDescription("安防领域知名品牌");

        ExtractionNormalizer.Entity haiKang2 = new ExtractionNormalizer.Entity();
        haiKang2.setText("海康");
        haiKang2.setLabel("品牌");
        haiKang2.setDescription("海康威视，全球领先的视频监控设备厂商");

        ExtractionNormalizer.Relation rel = new ExtractionNormalizer.Relation();
        rel.setSource("大华");
        rel.setTarget("视联网");
        rel.setLabel("接入");

        ExtractionNormalizer.ExtractionResult first = new ExtractionNormalizer.ExtractionResult();
        first.setEntities(new ArrayList<>(List.of(haiKang1)));
        first.setRelations(new ArrayList<>());

        ExtractionNormalizer.ExtractionResult gleaned = new ExtractionNormalizer.ExtractionResult();
        gleaned.setEntities(new ArrayList<>(List.of(daHua, haiKang2)));
        gleaned.setRelations(new ArrayList<>(List.of(rel)));

        ExtractionNormalizer.ExtractionResult merged =
                GraphBuildConsumer.mergeExtractionResults(first, gleaned);

        // 同名实体去重：海康 1 个、大华 1 个
        assertEquals(2, merged.getEntities().size());
        // description 择长：海康保留补捞轮更长的描述
        ExtractionNormalizer.Entity mergedHaiKang = merged.getEntities().stream()
                .filter(e -> e.getText().equals("海康")).findFirst().orElseThrow();
        assertEquals("海康威视，全球领先的视频监控设备厂商", mergedHaiKang.getDescription());
        // 关系端点解析到规范实体名
        assertEquals(1, merged.getRelations().size());
        assertEquals("大华", merged.getRelations().get(0).getSource());
    }

    // ==================== 辅助 ====================

    private ExtractionNormalizer.ExtractionResult result(
            ExtractionNormalizer.Entity entity, ExtractionNormalizer.Relation relation) {
        ExtractionNormalizer.ExtractionResult r = new ExtractionNormalizer.ExtractionResult();
        r.setEntities(entity == null ? new ArrayList<>() : new ArrayList<>(List.of(entity)));
        r.setRelations(relation == null ? new ArrayList<>() : new ArrayList<>(List.of(relation)));
        return r;
    }
}
