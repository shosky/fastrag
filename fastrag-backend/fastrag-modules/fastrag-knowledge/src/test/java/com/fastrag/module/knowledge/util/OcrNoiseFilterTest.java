package com.fastrag.module.knowledge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 嵌入图片 OCR 噪声过滤测试：用例来自小微ICT知识库截图 OCR 的真实噪声
 * （CRM 表单占位符、浏览器搜索框、界面按钮）与应保留的正常 OCR 文本。
 */
class OcrNoiseFilterTest {

    @Test
    void noise_formPlaceholdersAndButtons_dropped() {
        assertTrue(OcrNoiseFilter.isNoiseLine("客户经理：请输入"));
        assertTrue(OcrNoiseFilter.isNoiseLine("请输入客户联系人"));
        assertTrue(OcrNoiseFilter.isNoiseLine("[输入要求] 业务类型"));
        assertTrue(OcrNoiseFilter.isNoiseLine("百度一下"));
        assertTrue(OcrNoiseFilter.isNoiseLine("确定"));
        assertTrue(OcrNoiseFilter.isNoiseLine("360搜索"));
        assertTrue(OcrNoiseFilter.isNoiseLine("高级搜索"));
    }

    @Test
    void noise_shortMixedAndEnglishLabels_dropped() {
        // 短行中文+数字混合
        assertTrue(OcrNoiseFilter.isNoiseLine("充值110"));
        assertTrue(OcrNoiseFilter.isNoiseLine("账户名称22"));
        // 纯英文控件标签（中文域文档截图）
        assertTrue(OcrNoiseFilter.isNoiseLine("Bottom Button"));
        assertTrue(OcrNoiseFilter.isNoiseLine("Home"));
        // 纯数字/时间
        assertTrue(OcrNoiseFilter.isNoiseLine("2024-04-19 11:56"));
        assertTrue(OcrNoiseFilter.isNoiseLine("13348612222"));
        // 乱码
        assertTrue(OcrNoiseFilter.isNoiseLine("廉控状態ライザー"));
        assertTrue(OcrNoiseFilter.isNoiseLine("移动окрайвер"));
    }

    @Test
    void keep_legitimateOcrLines() {
        assertFalse(OcrNoiseFilter.isNoiseLine("小微ICT业务受理操作手册"));
        assertFalse(OcrNoiseFilter.isNoiseLine("点击【提交受理】后系统自动带出报价单数据"));
        assertFalse(OcrNoiseFilter.isNoiseLine("订单编号：XWICT20240419001（共3个服务包）"));
        // 长英文正文行不是控件标签
        assertFalse(OcrNoiseFilter.isNoiseLine("This is the first longer line of left column body text"));
    }

    @Test
    void sanitize_keepsValidLines_dropsNoise_preservesOrder() {
        String ocr = "受理成功后进入收费页面\n"
                + "百度一下\n"
                + "客户经理：请输入\n"
                + "\n"
                + "点击【去收费】完成缴费\n"
                + "充值110\n";
        String result = OcrNoiseFilter.sanitize(ocr);
        assertEquals("受理成功后进入收费页面\n点击【去收费】完成缴费", result);
    }

    @Test
    void sanitize_emptyAndAllNoise_returnEmpty() {
        assertEquals("", OcrNoiseFilter.sanitize(null));
        assertEquals("", OcrNoiseFilter.sanitize("  "));
        assertEquals("", OcrNoiseFilter.sanitize("百度一下\n确定\n取消"));
    }
}
