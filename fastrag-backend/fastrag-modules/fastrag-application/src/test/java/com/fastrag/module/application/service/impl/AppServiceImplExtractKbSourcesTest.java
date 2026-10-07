package com.fastrag.module.application.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QF-1（idd-review 快修）：extractKbSources 引用投影补 fileId。
 * 上游 RetrievalKbOperationService 结构化通道（query_kb result.data.chunks）含 file_id，
 * 投影必须保留（references 落库与前端契约依赖），不得再丢弃。
 */
@ExtendWith(MockitoExtension.class)
class AppServiceImplExtractKbSourcesTest {

    @InjectMocks
    private AppServiceImpl service;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extract(String toolCallsJson) {
        return (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service, "extractKbSources", toolCallsJson);
    }

    @Test
    void extractKbSources_keepsFileIdFromUpstreamChunks() {
        String toolCallsJson = """
                [
                  {
                    "name": "query_kb",
                    "result": {
                      "data": {
                        "kb_id": "kb-1",
                        "display_type": "knowledge_chunks",
                        "chunks": [
                          {"source": "a.pdf", "score": 0.91, "file_id": "file-1",
                           "chunk_index": 0, "content": "text-a"},
                          {"source": "b.docx", "score": 0.88, "file_id": "file-2",
                           "chunk_index": 3, "content": "text-b"}
                        ]
                      }
                    }
                  },
                  {"name": "other_tool", "result": {"data": {"chunks": [{"source": "x", "file_id": "file-9"}]}}}
                ]
                """;

        List<Map<String, Object>> sources = extract(toolCallsJson);

        assertThat(sources).hasSize(2); // other_tool 的 chunks 不参与投影
        assertThat(sources.get(0).get("fileId")).isEqualTo("file-1");
        assertThat(sources.get(1).get("fileId")).isEqualTo("file-2");
    }

    @Test
    void extractKbSources_keepsExistingProjectionFields() {
        String toolCallsJson = """
                [
                  {
                    "name": "query_kb",
                    "result": {
                      "data": {
                        "kb_id": "kb-7",
                        "chunks": [
                          {"source": "a.pdf", "score": 0.75, "file_id": "file-1",
                           "chunk_index": 2, "content": "text-a"}
                        ]
                      }
                    }
                  }
                ]
                """;

        List<Map<String, Object>> sources = extract(toolCallsJson);

        assertThat(sources).hasSize(1);
        Map<String, Object> src = sources.get(0);
        assertThat(src.get("fileName")).isEqualTo("a.pdf");
        assertThat(((Number) src.get("score")).doubleValue()).isEqualTo(0.75); // hutool 解析为 BigDecimal
        assertThat(src.get("kbId")).isEqualTo("kb-7");
        assertThat(src.get("chunkIndex")).isEqualTo(2);
        assertThat(src.get("content")).isEqualTo("text-a");
    }

    // ========================================================================
    //  R2（P0-5）句柄协议：references 投影按 cN 解析回真实 id；
    //  模型引用未识别句柄时投影响不到即自然丢弃（fail-closed）
    // ========================================================================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extract(String toolCallsJson, Map<String, Object> handleMap) {
        return (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service, "extractKbSources", toolCallsJson, handleMap);
    }

    @Test
    void extractKbSources_withHandleMap_resolvesRealIdsFromRegistry() {
        String toolCallsJson = """
                [
                  {
                    "name": "query_kb",
                    "result": {
                      "data": {
                        "kb_id": "kb-shown",
                        "chunks": [
                          {"handle": "c2", "source": "a.pdf", "score": 0.91, "content": "text-a"}
                        ]
                      }
                    }
                  }
                ]
                """;
        Map<String, Object> handleMap = new java.util.HashMap<>();
        handleMap.put("c2", Map.of("kbId", "kb-real", "fileId", "file-real", "chunkIndex", 5));

        List<Map<String, Object>> sources = extract(toolCallsJson, handleMap);

        assertThat(sources).hasSize(1);
        Map<String, Object> src = sources.get(0);
        assertThat(src.get("fileId")).isEqualTo("file-real");
        assertThat(src.get("kbId")).isEqualTo("kb-real");
        assertThat(src.get("chunkIndex")).isEqualTo(5);
        assertThat(src.get("fileName")).isEqualTo("a.pdf");
        assertThat(src.get("content")).isEqualTo("text-a");
    }

    @Test
    void extractKbSources_unknownHandle_droppedFailClosed_legacyChunksKept() {
        String toolCallsJson = """
                [
                  {
                    "name": "query_kb",
                    "result": {
                      "data": {
                        "kb_id": "kb-1",
                        "chunks": [
                          {"handle": "c9", "source": "ghost.pdf", "score": 0.9, "content": "未注册句柄"},
                          {"source": "legacy.pdf", "score": 0.8, "file_id": "file-legacy",
                           "chunk_index": 1, "content": "无句柄旧条目"}
                        ]
                      }
                    }
                  }
                ]
                """;
        Map<String, Object> handleMap = Map.of(
                "c1", Map.of("kbId", "kb-1", "fileId", "file-1", "chunkIndex", 0));

        List<Map<String, Object>> sources = extract(toolCallsJson, handleMap);

        // c9 不在注册表 → fail-closed 丢弃；无 handle 条目走旧投影保留
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).get("fileId")).isEqualTo("file-legacy");
        assertThat(sources.get(0).get("fileName")).isEqualTo("legacy.pdf");
    }

    @Test
    void extractKbSources_handlePresentButRegistryNull_droppedFailClosed() {
        String toolCallsJson = """
                [
                  {
                    "name": "query_kb",
                    "result": {
                      "data": {
                        "kb_id": "kb-1",
                        "chunks": [
                          {"handle": "c1", "source": "a.pdf", "score": 0.9, "content": "t"}
                        ]
                      }
                    }
                  }
                ]
                """;

        List<Map<String, Object>> sources = extract(toolCallsJson, null);

        assertThat(sources).isEmpty();
    }
}
