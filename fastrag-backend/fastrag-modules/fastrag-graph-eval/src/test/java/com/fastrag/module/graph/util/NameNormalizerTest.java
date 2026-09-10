package com.fastrag.module.graph.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 实体名称标准化测试：全半角归一、尾部数量后缀剥离、大小写与空白折叠。
 * 核心场景：同一设备的不同书写（数量后缀/全角括号/大小写）必须归一到相同结果，
 * 保证确定性 ID 一致、实体自动合并。
 */
class NameNormalizerTest {

    @Test
    void normalize_basicTrimLowercaseCollapse() {
        // 中英文之间空格按 LightRAG 规则去除（2026-09 引入，与"小微 ICT"归一同理）
        assertEquals("crm系统", NameNormalizer.normalize("  CRM   系统 "));
        assertEquals("", NameNormalizer.normalize(null));
        assertEquals("", NameNormalizer.normalize(""));
    }

    @Test
    void normalize_fullWidthToHalfWidth() {
        // 全角括号/冒号/字母数字 → 半角，避免"（云化版）"与"(云化版)"分裂成两个实体
        assertEquals("tp link ct4ws-p v2(云化版)", NameNormalizer.normalize("TP LINK CT4WS-P V2（云化版）"));
        assertEquals("abc123", NameNormalizer.normalize("ＡＢＣ１２３"));
        assertEquals("a:b", NameNormalizer.normalize("A：B"));
    }

    @Test
    void normalize_stripsTrailingQuantitySuffix() {
        // 生产数据：数量被烤进实体名（TP LINK CT4WS-P V2(云化版)*4个），剥离后与无数量版本合并
        assertEquals("tp link ct4ws-p v2(云化版)", NameNormalizer.normalize("TP LINK CT4WS-P V2(云化版)*4个"));
        assertEquals("tp link ct4ws-p v2(云化版)", NameNormalizer.normalize("TP LINK CT4WS-P V2(云化版)＊4个"));
        assertEquals("敏觉smart-d2r-240-p", NameNormalizer.normalize("敏觉 Smart-D2R-240-P×2"));
        // 括号数量后缀
        assertEquals("tp link ct4ws-p v2", NameNormalizer.normalize("TP LINK CT4WS-P V2(4个)"));
    }

    @Test
    void normalize_stripsCjkAdjacentSpaces_lightRagRule() {
        // LightRAG 同款规则：中文间空格、中文与英文/数字间空格必须归一，
        // 否则"小微 ICT"与"小微ICT"在合并层分裂成两个节点
        assertEquals(NameNormalizer.normalize("小微ICT"), NameNormalizer.normalize("小微 ICT"));
        assertEquals("小微ict", NameNormalizer.normalize("小微 ICT"));
        assertEquals("华为b671-s2", NameNormalizer.normalize("华为 B671-S2"));
        assertEquals("4个摄像头", NameNormalizer.normalize("4 个摄像头"));
        assertEquals("全光组网", NameNormalizer.normalize("全光 组网"));
        // 纯英文之间的空格保留
        assertEquals("tp link ct4ws-p v2", NameNormalizer.normalize("TP LINK CT4WS-P V2"));
    }

    @Test
    void normalize_keepsLeadingQuantityAndModelNumbers() {
        // 数量前缀有业务语义，不剥离
        assertEquals("4个摄像头", NameNormalizer.normalize("4个摄像头"));
        assertEquals("1主1从标准包", NameNormalizer.normalize("1主1从标准包"));
        // 型号中间/结尾的数字与 x 不能误伤（RTX 式名称）
        assertEquals("rtx 4090", NameNormalizer.normalize("RTX 4090"));
        assertEquals("华为b866-s2-5e4p5w1", NameNormalizer.normalize("华为B866-S2-5E4P5W1"));
    }

    @Test
    void normalize_neverReturnsEmptyForPureSuffixName() {
        // 名称本身就是 "*2" 时放弃剥离，避免产生空归一名
        assertEquals("*2", NameNormalizer.normalize("*2"));
    }
}
