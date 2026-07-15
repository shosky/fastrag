package com.fastrag.module.tools.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.mapper.McpServiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 内置服务种子数据初始化器。
 * <p>
 * 增量同步模式：内置服务列表硬编码在此，启动时与 DB 对比，
 * 不存在的创建，已存在的更新描述/配置（但不覆盖用户修改的 enabled 状态）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpBuiltinSeeder implements CommandLineRunner {

    private final McpServiceMapper mapper;

    private static final List<Map<String, Object>> BUILTIN_SERVICES = List.of(
        Map.of(
            "slug", "filesystem",
            "name", "文件系统",
            "description", "提供文件和目录的读写、搜索、编辑等操作能力",
            "transport", "stdio",
            "command", "npx",
            "args", List.of("-y", "@modelcontextprotocol/server-filesystem"),
            "enabled", 1
        ),
        Map.of(
            "slug", "fetch",
            "name", "网页抓取",
            "description", "获取网页内容并转换为 Markdown 格式",
            "transport", "stdio",
            "command", "uvx",
            "args", List.of("mcp-server-fetch"),
            "enabled", 0
        ),
        Map.of(
            "slug", "sequential-thinking",
            "name", "链式思维",
            "description", "通过思维链工具增强 LLM 的推理能力",
            "transport", "stdio",
            "command", "npx",
            "args", List.of("-y", "@modelcontextprotocol/server-sequential-thinking"),
            "enabled", 0
        )
    );

    @Override
    public void run(String... args) {
        log.info("[McpBuiltinSeeder] 开始初始化内置 MCP 服务...");

        for (Map<String, Object> config : BUILTIN_SERVICES) {
            try {
                syncBuiltinService(config);
            } catch (Exception e) {
                log.error("[McpBuiltinSeeder] 同步 MCP 服务失败: {}", config.get("slug"), e);
            }
        }

        log.info("[McpBuiltinSeeder] 内置 MCP 服务初始化完成，共 {} 个", BUILTIN_SERVICES.size());
    }

    @SuppressWarnings("unchecked")
    private void syncBuiltinService(Map<String, Object> config) {
        String slug = (String) config.get("slug");

        // 检查是否已存在内置服务
        McpService existing = mapper.selectOne(
            new LambdaQueryWrapper<McpService>()
                .eq(McpService::getSlug, slug)
                .eq(McpService::getIsBuiltin, 1));

        if (existing != null) {
            // 更新已有服务（不覆盖 enabled、status、lastUsed 等用户可修改字段）
            existing.setName((String) config.get("name"));
            existing.setDescription((String) config.getOrDefault("description", ""));
            existing.setTransport((String) config.getOrDefault("transport", "stdio"));
            existing.setCommand((String) config.get("command"));
            existing.setArgs((List<String>) config.get("args"));
            // 不更新 enabled，保留用户设置
            mapper.updateById(existing);
            log.debug("[McpBuiltinSeeder] 更新内置 MCP 服务: {} ({})", config.get("name"), slug);
        } else {
            // 创建新服务
            McpService service = new McpService();
            service.setSlug(slug);
            service.setName((String) config.get("name"));
            service.setDescription((String) config.getOrDefault("description", ""));
            service.setTransport((String) config.getOrDefault("transport", "stdio"));
            service.setCommand((String) config.get("command"));
            service.setArgs((List<String>) config.get("args"));
            service.setIsBuiltin(1);
            service.setEnabled((Integer) config.getOrDefault("enabled", 0));
            service.setStatus("offline");
            service.setCreatedAt(LocalDateTime.now());
            mapper.insert(service);
            log.info("[McpBuiltinSeeder] 创建内置 MCP 服务: {} ({})", config.get("name"), slug);
        }
    }
}
