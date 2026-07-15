package com.fastrag.module.agent.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 沙箱文件操作后端。
 * 通过 HTTP API 调用 Docker 容器内的文件系统和 Shell 操作。
 *
 * <p>当沙箱不可用时，所有方法返回错误字符串。</p>
 */
@Slf4j
@Component
public class SandboxBackend {

    private final SandboxService sandboxService;
    private final WebClient.Builder webClientBuilder;

    /** 可读根路径 */
    private static final Set<String> READABLE_ROOTS = Set.of(
            "/home/gem/user-data", "/home/gem/skills");

    /** 可写根路径 */
    private static final Set<String> WRITABLE_ROOTS = Set.of(
            "/home/gem/user-data/workspace", "/home/gem/user-data/outputs");

    public SandboxBackend(SandboxService sandboxService, WebClient.Builder webClientBuilder) {
        this.sandboxService = sandboxService;
        this.webClientBuilder = webClientBuilder;
    }

    // ==================== 文件读取 ====================

    /**
     * 读取文件内容。
     */
    public String readFile(String uid, String fileThreadId, String filePath,
                           Integer offset, Integer limit) {
        SandboxConnection conn = sandboxService.get(uid, fileThreadId);
        if (conn == null) return "Error: Sandbox not available";

        if (!isReadable(filePath)) return "Error: Path not readable: " + filePath;
        if (isTraversal(filePath)) return "Error: Path traversal not allowed";

        try {
            String url = conn.getUrl() + "/api/files" + normalizePath(filePath);
            return client(conn).get()
                    .uri(uri -> {
                        var u = uri.path("/api/files" + normalizePath(filePath));
                        if (offset != null) u.queryParam("offset", offset);
                        if (limit != null) u.queryParam("limit", limit);
                        return u.build();
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ==================== 文件写入 ====================

    /**
     * 写入文件内容。
     */
    public String writeFile(String uid, String fileThreadId, String filePath, String content) {
        SandboxConnection conn = sandboxService.get(uid, fileThreadId);
        if (conn == null) return "Error: Sandbox not available";

        if (!isWritable(filePath)) return "Error: Path not writable: " + filePath;
        if (isTraversal(filePath)) return "Error: Path traversal not allowed";

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("path", filePath);
            body.put("content", content);

            return client(conn).post()
                    .uri("/api/files")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ==================== 文件编辑 ====================

    /**
     * 编辑文件（str_replace 模式）。
     */
    public String editFile(String uid, String fileThreadId, String filePath,
                           String oldString, String newString, boolean replaceAll) {
        SandboxConnection conn = sandboxService.get(uid, fileThreadId);
        if (conn == null) return "Error: Sandbox not available";

        if (!isWritable(filePath)) return "Error: Path not writable: " + filePath;

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("path", filePath);
            body.put("old_string", oldString);
            body.put("new_string", newString);
            body.put("replace_all", replaceAll);

            return client(conn).patch()
                    .uri("/api/files")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ==================== Shell 执行 ====================

    /**
     * 在沙箱中执行 Shell 命令。
     */
    public String execute(String uid, String fileThreadId, String command,
                          Integer timeout, String cwd) {
        SandboxConnection conn = sandboxService.get(uid, fileThreadId);
        if (conn == null) return "Error: Sandbox not available";

        int effectiveTimeout = timeout != null ? timeout : 120;

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("command", command);
            body.put("timeout", effectiveTimeout);
            if (cwd != null) body.put("cwd", cwd);

            String result = client(conn).post()
                    .uri("/api/execute")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(effectiveTimeout + 10));

            // 截断过长输出（262KB）
            if (result != null && result.length() > 262_144) {
                result = result.substring(0, 262_144) + "\n...(truncated)";
            }
            return result != null ? result : "No output";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ==================== 目录列表 ====================

    /**
     * 列出目录内容。
     */
    public String listDirectory(String uid, String fileThreadId, String path) {
        SandboxConnection conn = sandboxService.get(uid, fileThreadId);
        if (conn == null) return "Error: Sandbox not available";

        try {
            return client(conn).get()
                    .uri("/api/directories" + normalizePath(path))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ==================== 搜索操作 ====================

    /**
     * 搜索文件内容。
     */
    public String grep(String uid, String fileThreadId, String pattern,
                       String path, String glob) {
        SandboxConnection conn = sandboxService.get(uid, fileThreadId);
        if (conn == null) return "Error: Sandbox not available";

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pattern", pattern);
            body.put("path", path);
            if (glob != null) body.put("glob", glob);

            return client(conn).post()
                    .uri("/api/search/grep")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ==================== 路径匹配 ====================

    /**
     * 按模式匹配文件。
     */
    public String glob(String uid, String fileThreadId, String pattern, String path) {
        SandboxConnection conn = sandboxService.get(uid, fileThreadId);
        if (conn == null) return "Error: Sandbox not available";

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pattern", pattern);
            body.put("path", path);

            return client(conn).post()
                    .uri("/api/search/glob")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ==================== 权限控制 ====================

    private boolean isReadable(String path) {
        String normalized = normalizePath(path);
        return READABLE_ROOTS.stream().anyMatch(normalized::startsWith);
    }

    private boolean isWritable(String path) {
        String normalized = normalizePath(path);
        return WRITABLE_ROOTS.stream().anyMatch(normalized::startsWith);
    }

    private boolean isTraversal(String path) {
        return path != null && path.contains("..");
    }

    private String normalizePath(String path) {
        if (path == null) return "/";
        return path.replace("\\", "/").replaceAll("/+", "/");
    }

    private WebClient client(SandboxConnection conn) {
        return webClientBuilder.clone().baseUrl(conn.getUrl()).build();
    }
}
