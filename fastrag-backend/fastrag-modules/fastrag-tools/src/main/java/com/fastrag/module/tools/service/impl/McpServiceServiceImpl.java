package com.fastrag.module.tools.service.impl;
import cn.hutool.core.util.StrUtil; import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.*; import com.fastrag.module.tools.mapper.*;
import com.fastrag.module.tools.service.McpServiceService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class McpServiceServiceImpl implements McpServiceService {
    private final McpServiceMapper mapper; private final McpToolMapper toolMapper;
    @Override public List<McpService> list(String kw) { var w=new LambdaQueryWrapper<McpService>(); if(StrUtil.isNotBlank(kw))w.like(McpService::getName,kw); return mapper.selectList(w); }
    @Override public McpService get(String id) { return mapper.selectById(id); }
    @Override public McpService create(Map<String,Object> f) { var s=new McpService(); s.setName((String)f.get("name")); s.setMcpUrl((String)f.get("mcpUrl")); s.setAuthType((String)f.getOrDefault("authType","none")); s.setEnabled(1); s.setStatus("offline"); mapper.insert(s); return s; }
    @Override public McpService update(String id,Map<String,Object> f) { var s=mapper.selectById(id); if(s!=null){if(f.containsKey("name"))s.setName((String)f.get("name")); mapper.updateById(s);} return s; }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public void toggleEnabled(String id) { var s=mapper.selectById(id); if(s!=null){s.setEnabled(s.getEnabled()==1?0:1);mapper.updateById(s);} }
    @Override public List<McpTool> listTools(String serviceId) { return toolMapper.selectList(new LambdaQueryWrapper<McpTool>().eq(McpTool::getServiceId,serviceId)); }
}
