package com.fastrag.module.tools.service;
import com.fastrag.module.tools.entity.*; import java.util.*;

public interface McpServiceService {
    List<McpService> list(String keyword);
    List<McpService> listEnabled();
    List<McpService> listBuiltin();
    McpService get(String id);
    McpService getBySlug(String slug);
    McpService create(Map<String, Object> form);
    McpService update(String id, Map<String, Object> form);
    void delete(String id);
    void toggleEnabled(String id);
    void toggleTool(Long toolId);
    List<McpTool> listTools(String serviceId);
    boolean existsBySlug(String slug);
}
