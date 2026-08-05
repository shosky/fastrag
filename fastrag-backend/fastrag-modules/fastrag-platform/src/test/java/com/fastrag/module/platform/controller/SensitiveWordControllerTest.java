package com.fastrag.module.platform.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.platform.entity.SensitiveWord;
import com.fastrag.module.platform.service.SensitiveWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveWordControllerTest {

    @Mock
    private SensitiveWordService sensitiveWordService;

    private SensitiveWordController controller;

    @BeforeEach
    void setUp() {
        controller = new SensitiveWordController(sensitiveWordService);
    }

    private SensitiveWord makeWord(Long id, String word, String replacement, String category, int enabled) {
        SensitiveWord sw = new SensitiveWord();
        sw.setId(id);
        sw.setWord(word);
        sw.setReplacement(replacement);
        sw.setCategory(category);
        sw.setEnabled(enabled);
        sw.setLevel("high");
        sw.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0, 0));
        return sw;
    }

    @Test
    @SuppressWarnings("unchecked")
    void testList_returnsFrontendCompatibleFormat() {
        SensitiveWord w1 = makeWord(1L, "违禁词", "请文明发言",
                "{\"blockInput\":true,\"blockSearch\":false,\"replaceAnswer\":false}", 1);
        when(sensitiveWordService.list()).thenReturn(List.of(w1));

        ApiResponse<?> response = controller.list();

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getData();
        assertEquals(1, data.size());
        Map<String, Object> item = data.get(0);

        // Verify frontend-expected flat field names
        assertEquals("违禁词", item.get("word"));
        assertEquals("请文明发言", item.get("reply"));
        assertTrue((Boolean) item.get("blockInput"));
        assertFalse((Boolean) item.get("blockSearch"));
        assertFalse((Boolean) item.get("replaceAnswer"));
        assertEquals("high", item.get("level"));
        assertTrue((Boolean) item.get("enabled"));
        // Must NOT contain raw entity field names
        assertNull(item.get("replacement"), "Should not expose raw 'replacement' field");
        assertNull(item.get("category"), "Should not expose raw 'category' JSON field");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testList_emptyList() {
        when(sensitiveWordService.list()).thenReturn(List.of());
        ApiResponse<?> response = controller.list();
        assertEquals(200, response.getCode());
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getData();
        assertTrue(data.isEmpty());
    }

    @Test
    void testTemplate_returnsCsv() {
        ResponseEntity<byte[]> response = controller.downloadTemplate();
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        String csv = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("敏感词"), "Template should contain CSV header");
        assertTrue(csv.contains("阻止用户输入"), "Template should contain column header");
        assertTrue(csv.contains("违禁词"), "Template should contain sample row");
        assertEquals("attachment; filename=sensitive_words_template.csv",
                response.getHeaders().getFirst("Content-Disposition"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testList_mapsMultipleWordsCorrectly() {
        SensitiveWord w1 = makeWord(1L, "广告", "***",
                "{\"blockInput\":false,\"blockSearch\":true,\"replaceAnswer\":true}", 1);
        SensitiveWord w2 = makeWord(2L, "暴力", "",
                "{\"blockInput\":true,\"blockSearch\":true,\"replaceAnswer\":false}", 1);
        when(sensitiveWordService.list()).thenReturn(List.of(w1, w2));

        ApiResponse<?> response = controller.list();
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getData();
        assertEquals(2, data.size());

        Map<String, Object> first = data.get(0);
        assertEquals("***", first.get("reply"));
        assertFalse((Boolean) first.get("blockInput"));
        assertTrue((Boolean) first.get("blockSearch"));
        assertTrue((Boolean) first.get("replaceAnswer"));

        Map<String, Object> second = data.get(1);
        assertEquals("", second.get("reply"));
        assertTrue((Boolean) second.get("blockInput"));
    }
}
