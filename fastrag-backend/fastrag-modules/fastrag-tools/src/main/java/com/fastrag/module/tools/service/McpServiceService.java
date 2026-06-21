package com.fastrag.module.tools.service;
import com.fastrag.module.tools.entity.*; import java.util.*;
public interface McpServiceService { List<McpService> list(String keyword); McpService get(String id); McpService create(Map<String,Object> form); McpService update(String id,Map<String,Object> form); void delete(String id); void toggleEnabled(String id); List<McpTool> listTools(String serviceId); }
