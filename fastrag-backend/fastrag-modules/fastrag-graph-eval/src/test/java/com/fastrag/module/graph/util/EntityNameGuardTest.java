package com.fastrag.module.graph.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体名清洗闸门测试：用例全部来自小微ICT知识库图谱的真实噪声数据
 * （截图 OCR 字段残留、幻觉乱码、表格碎片、表头泛化词）。
 */
class EntityNameGuardTest {

    @Test
    void junk_valueLikeContent() {
        assertTrue(EntityNameGuard.isJunkName(null));
        assertTrue(EntityNameGuard.isJunkName("a"));
        assertTrue(EntityNameGuard.isJunkName("0"));
        assertTrue(EntityNameGuard.isJunkName("445元/月"));
        assertTrue(EntityNameGuard.isJunkName("103天"));
        assertTrue(EntityNameGuard.isJunkName("2024年4月19日"));
        assertTrue(EntityNameGuard.isJunkName("https://example.com/x"));
        assertTrue(EntityNameGuard.isJunkName("13348612222"));
        assertTrue(EntityNameGuard.isJunkName("XWICT0731101299"));
        assertTrue(EntityNameGuard.isJunkName("ZQ12345"));
        assertTrue(EntityNameGuard.isJunkName("local-btn"));
        assertTrue(EntityNameGuard.isJunkName("一次性费用由"));
        assertTrue(EntityNameGuard.isJunkName("1主"));
    }

    @Test
    void junk_keyValuePairs_fullAndHalfWidthColon() {
        // 表单字段值对被实体化（生产数据：商品数量：10、业务号：XWICT07311101174）
        assertTrue(EntityNameGuard.isJunkName("商品数量：10"));
        assertTrue(EntityNameGuard.isJunkName("商品状态：审核"));
        assertTrue(EntityNameGuard.isJunkName("业务号： XWICT07311101174"));
        assertTrue(EntityNameGuard.isJunkName("traceld:134188145070"));
    }

    @Test
    void junk_crossLanguageGarbage() {
        // OCR/幻觉产生的假名与西里尔混杂（生产数据原样）
        assertTrue(EntityNameGuard.isJunkName("SiGi 廉控状态Aライザーロール"));
        assertTrue(EntityNameGuard.isJunkName("移动окрайвер"));
    }

    @Test
    void junk_trailingAmountAndLongCodes() {
        // 表格碎片截断出的伪产品名（生产数据：头标准包2999元）
        assertTrue(EntityNameGuard.isJunkName("头标准包2999元"));
        assertTrue(EntityNameGuard.isJunkName("叠加1个摄像头868元"));
        // 无分隔符字母数字长编码（业务编号）
        assertTrue(EntityNameGuard.isJunkName("1HWZX73120240463884"));
    }

    @Test
    void junk_genericTableHeaderWords() {
        // BOM 价目表表头被抽成实体后成为垃圾汇聚节点（生产数据：总价/价格/型号）
        assertTrue(EntityNameGuard.isJunkName("总价"));
        assertTrue(EntityNameGuard.isJunkName("价格"));
        assertTrue(EntityNameGuard.isJunkName("型号"));
        assertTrue(EntityNameGuard.isJunkName("工费"));
        assertTrue(EntityNameGuard.isJunkName("辅材"));
    }

    @Test
    void junk_attributeTypedEntities_evenWithoutColon() {
        // 生产数据：截图表单字段抽成无冒号的"属性"实体（客户联系/失败时间/受理时间），
        // 仅靠名称模式拦不住，必须借类型信号（2026-09-07 新增）
        assertTrue(EntityNameGuard.isJunkName("客户联系", "属性"));
        assertTrue(EntityNameGuard.isJunkName("失败时间", "属性"));
        assertTrue(EntityNameGuard.isJunkName("业务号码网络归属", "属性"));
        // 非属性类型不受影响
        assertFalse(EntityNameGuard.isJunkName("天翼云眼", "服务"));
        // 类型为 null 退化为纯名称判定
        assertFalse(EntityNameGuard.isJunkName("天翼云眼", null));
    }

    @Test
    void keep_legitimateDomainEntities() {
        // 设备型号（以数字结尾不能误杀）
        assertFalse(EntityNameGuard.isJunkName("华为B671-S2"));
        assertFalse(EntityNameGuard.isJunkName("华为B866-S2-5E4P5W1"));
        assertFalse(EntityNameGuard.isJunkName("TP LINK CT4WS-P V2"));
        assertFalse(EntityNameGuard.isJunkName("TP-LINK 内存卡 256G TL-SD256"));
        // 业务概念
        assertFalse(EntityNameGuard.isJunkName("小微ICT业务"));
        assertFalse(EntityNameGuard.isJunkName("营销六步法"));
        assertFalse(EntityNameGuard.isJunkName("黄金四问法"));
        assertFalse(EntityNameGuard.isJunkName("FTTR-B全光组网"));
        assertFalse(EntityNameGuard.isJunkName("全光组网标准礼包-小微ICT标准包"));
        assertFalse(EntityNameGuard.isJunkName("天翼云眼"));
        // 数量前缀名称（有业务语义，保留）
        assertFalse(EntityNameGuard.isJunkName("4个摄像头"));
        assertFalse(EntityNameGuard.isJunkName("5G网优"));
    }
}
