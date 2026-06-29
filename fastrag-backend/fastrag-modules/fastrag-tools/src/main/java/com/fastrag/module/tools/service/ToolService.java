package com.fastrag.module.tools.service;
import com.fastrag.module.tools.entity.*; import java.util.*;
import org.springframework.web.multipart.MultipartFile;
public interface ToolService {
    List<Tool> list(String keyword,String type); Tool get(String id);
    Tool create(Map<String,Object> form); Tool update(String id,Map<String,Object> form);
    void delete(String id); void toggleEnabled(String id);
    ToolHttpConfig getApiConfig(String toolId);
    ToolHttpConfig saveApiConfig(String toolId,ToolHttpConfig config);
    // 插件管理扩展
    Tool uploadPlugin(MultipartFile file, String name, String description);
    List<Tool> importFromJson(List<Map<String,Object>> plugins);
}
