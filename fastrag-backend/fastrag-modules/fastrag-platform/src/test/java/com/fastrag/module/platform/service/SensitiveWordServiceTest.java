package com.fastrag.module.platform.service;

import com.fastrag.module.platform.entity.SensitiveWord;
import com.fastrag.module.platform.mapper.SensitiveWordMapper;
import com.fastrag.module.platform.service.impl.SensitiveWordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceTest {

    @Mock
    private SensitiveWordMapper sensitiveWordMapper;

    private SensitiveWordService sensitiveWordService;

    @BeforeEach
    void setUp() {
        sensitiveWordService = new SensitiveWordServiceImpl(sensitiveWordMapper);
    }

    private SensitiveWord makeWord(String word, String category, String replacement, int enabled) {
        SensitiveWord sw = new SensitiveWord();
        sw.setId(1L);
        sw.setWord(word);
        sw.setCategory(category);
        sw.setReplacement(replacement);
        sw.setEnabled(enabled);
        return sw;
    }

    @Test
    void testList_returnsEnabledWords() {
        SensitiveWord w1 = makeWord("badword", "{\"blockInput\":true}", "", 1);
        SensitiveWord w2 = makeWord("disabled", "{\"blockInput\":true}", "", 0);
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(w1));
        List<SensitiveWord> result = sensitiveWordService.list();
        assertEquals(1, result.size());
        assertEquals("badword", result.get(0).getWord());
    }

    @Test
    void testCheckAndFilter_rejectMode_withMatch() {
        SensitiveWord w1 = makeWord("暴力", "{\"blockInput\":true}", "", 1);
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(w1));
        Optional<String> result = sensitiveWordService.checkAndFilter("这是暴力内容", "reject");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("暴力"));
    }

    @Test
    void testCheckAndFilter_rejectMode_noMatch() {
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of());
        Optional<String> result = sensitiveWordService.checkAndFilter("这是正常内容", "reject");
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckAndFilter_replaceMode() {
        SensitiveWord w1 = makeWord("敏感词", "{\"blockInput\":true}", "***", 1);
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(w1));
        Optional<String> result = sensitiveWordService.checkAndFilter("这是一个敏感词测试", "replace");
        assertTrue(result.isPresent());
        assertEquals("这是一个***测试", result.get());
    }

    @Test
    void testCheckAndFilter_maskMode() {
        SensitiveWord w1 = makeWord("机密", "{\"blockInput\":true}", "", 1);
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(w1));
        Optional<String> result = sensitiveWordService.checkAndFilter("这是机密信息", "mask");
        assertTrue(result.isPresent());
        assertEquals("这是机*信息", result.get());
    }

    @Test
    void testCheckAndFilter_emptyText() {
        Optional<String> result = sensitiveWordService.checkAndFilter("", "reject");
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckInput_withBlockInputCategory() {
        SensitiveWord w1 = makeWord("违禁", "{\"blockInput\":true,\"blockSearch\":false,\"replaceAnswer\":false}", "", 1);
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(w1));
        Optional<String> result = sensitiveWordService.checkInput("包含违禁词");
        assertTrue(result.isPresent());
    }

    @Test
    void testFilterOutput_withReplaceAnswerCategory() {
        SensitiveWord w1 = makeWord("bad", "{\"replaceAnswer\":true}", "good", 1);
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(w1));
        Optional<String> result = sensitiveWordService.filterOutput("this is bad content");
        assertTrue(result.isPresent());
        assertEquals("this is good content", result.get());
    }
}
