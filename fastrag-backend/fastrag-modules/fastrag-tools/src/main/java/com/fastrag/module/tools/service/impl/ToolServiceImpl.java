package com.fastrag.module.tools.service.impl;
import cn.hutool.core.util.StrUtil; import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.Tool; import com.fastrag.module.tools.entity.ToolHttpConfig;
import com.fastrag.module.tools.mapper.ToolMapper; import com.fastrag.module.tools.mapper.ToolHttpConfigMapper;
import com.fastrag.module.tools.service.ToolService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@Service @RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {
    private final ToolMapper mapper;
    private final ToolHttpConfigMapper httpMapper;
    @Override public List<Tool> list(String kw,String type) { var w=new LambdaQueryWrapper<Tool>(); if(StrUtil.isNotBlank(kw))w.like(Tool::getName,kw); if(StrUtil.isNotBlank(type))w.eq(Tool::getType,type); return mapper.selectList(w); }
    @Override public Tool get(String id) { return mapper.selectById(id); }
    @Override public Tool create(Map<String,Object> f) { var t=new Tool(); t.setName((String)f.get("name")); t.setIdentifier((String)f.get("identifier")); t.setDescription((String)f.get("description")); t.setType((String)f.getOrDefault("type","http")); t.setEnabled(1); mapper.insert(t); return t; }
    @Override public Tool update(String id,Map<String,Object> f) { var t=mapper.selectById(id); if(t!=null){if(f.containsKey("name"))t.setName((String)f.get("name")); if(f.containsKey("description"))t.setDescription((String)f.get("description")); if(f.containsKey("identifier"))t.setIdentifier((String)f.get("identifier")); mapper.updateById(t);} return t; }
    @Override public void delete(String id) { mapper.deleteById(id); httpMapper.deleteById(id); }
    @Override public void toggleEnabled(String id) { var t=mapper.selectById(id); if(t!=null){t.setEnabled(t.getEnabled()==1?0:1);mapper.updateById(t);} }
    @Override public ToolHttpConfig getApiConfig(String toolId) {
        var cfg=httpMapper.selectById(toolId);
        if(cfg==null){ cfg=new ToolHttpConfig(); cfg.setToolId(toolId); cfg.setMethod("GET"); }
        return cfg;
    }
    @Override public ToolHttpConfig saveApiConfig(String toolId,ToolHttpConfig config) {
        config.setToolId(toolId);
        var existing=httpMapper.selectById(toolId);
        if(existing==null) httpMapper.insert(config); else httpMapper.updateById(config);
        return config;
    }

    // ===== 插件管理扩展 =====
    @Override public Tool uploadPlugin(MultipartFile file, String name, String description) {
        var t=new Tool();
        t.setName(name!=null?name:file.getOriginalFilename());
        t.setIdentifier(UUID.randomUUID().toString().substring(0,8));
        t.setDescription(description!=null?description:"通过上传创建的插件");
        t.setType("plugin");
        t.setEnabled(1);
        mapper.insert(t);
        return t;
    }

    @Override public List<Tool> importFromJson(List<Map<String,Object>> plugins) {
        List<Tool> result=new ArrayList<>();
        for(var f:plugins){
            var t=new Tool();
            t.setName((String)f.get("name"));
            t.setIdentifier((String)f.getOrDefault("identifier",UUID.randomUUID().toString().substring(0,8)));
            t.setDescription((String)f.get("description"));
            t.setType((String)f.getOrDefault("type","http"));
            t.setEnabled(1);
            mapper.insert(t);
            result.add(t);
        }
        return result;
    }
}
