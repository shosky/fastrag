package com.fastrag.module.knowledge.consumer;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.model.QaGenerationConfig;
import com.fastrag.module.knowledge.service.QaGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;

/**
 * 摄入挂钩测试（tdd/qa-generation.md S6 挂钩三例）。
 * IngestionConsumer 构造依赖 15 项，挂钩路径仅触达 qaGenerationService——其余以 null 注入；
 * triggerQaGeneration 为包私有测试缝（见 design/qa-generation.md §1 摄入挂钩变更点）。
 */
@ExtendWith(MockitoExtension.class)
class IngestionQaHookTest {

    @Mock private QaGenerationService qaGenerationService;

    private IngestionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new IngestionConsumer(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, qaGenerationService);
    }

    private KbFile qaFile(String configJson) {
        KbFile f = new KbFile();
        f.setId("f1");
        f.setKbId("kb1");
        f.setProcessingMode("qa");
        f.setQaGenerationConfig(configJson);
        return f;
    }

    @Test
    void 挂钩_配置已落库时_按参数replace语义触发() {
        consumer.triggerQaGeneration(qaFile(
                "{\"count\":5,\"customInstruction\":\"只出参数题\",\"model\":\"deepseek-v4\"}"));

        ArgumentCaptor<QaGenerationConfig> cap = ArgumentCaptor.forClass(QaGenerationConfig.class);
        verify(qaGenerationService).trigger(eq("kb1"), eq("f1"), cap.capture());
        assertEquals(5, cap.getValue().getCount());
        assertEquals("只出参数题", cap.getValue().getCustomInstruction());
        assertEquals("deepseek-v4", cap.getValue().getModel());
        assertEquals("replace", cap.getValue().getMode(), "文件级参数无 mode 字段——自动触发恒 replace 语义");
    }

    @Test
    void 挂钩_配置缺失时_全缺省replace触发() {
        consumer.triggerQaGeneration(qaFile(null));

        ArgumentCaptor<QaGenerationConfig> cap = ArgumentCaptor.forClass(QaGenerationConfig.class);
        verify(qaGenerationService).trigger(eq("kb1"), eq("f1"), cap.capture());
        assertNull(cap.getValue().getCount());
        assertNull(cap.getValue().getModel());
        assertEquals("replace", cap.getValue().getMode());
    }

    @Test
    void 挂钩_trigger异常时_直接上抛由调用点捕获() {
        when(qaGenerationService.trigger(any(), any(), any()))
                .thenThrow(BusinessException.badRequest("该文件已有进行中的提炼任务"));

        assertThrows(BusinessException.class,
                () -> consumer.triggerQaGeneration(qaFile(null)));
    }
}
