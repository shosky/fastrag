package com.fastrag.module.application.service.impl;
import cn.hutool.core.util.StrUtil; import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.application.entity.*; import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.AppService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class AppServiceImpl implements AppService {
    private final AppMapper appMapper; private final AppConfigMapper configMapper; private final AppTemplateMapper tplMapper;
    @Override public List<App> list(String kw,String tag) { var w=new LambdaQueryWrapper<App>(); if(StrUtil.isNotBlank(kw))w.like(App::getName,kw); if(StrUtil.isNotBlank(tag))w.like(App::getTags,tag); return appMapper.selectList(w.orderByDesc(App::getCreatedAt)); }
    @Override public App get(String id) { return appMapper.selectById(id); }
    @Override public App create(Map<String,Object> f) { var a=new App(); a.setName((String)f.get("name")); a.setDescription((String)f.get("description")); a.setType((String)f.getOrDefault("type","ChatBot")); a.setStatus("draft"); appMapper.insert(a); return a; }
    @Override public App update(String id,Map<String,Object> f) { var a=appMapper.selectById(id); if(a!=null){if(f.containsKey("name"))a.setName((String)f.get("name")); if(f.containsKey("description"))a.setDescription((String)f.get("description")); appMapper.updateById(a);} return a; }
    @Override public void delete(String id) { appMapper.deleteById(id); }
    @Override public List<AppTemplate> getTemplates() { return tplMapper.selectList(null); }
    @Override public AppConfig getConfig(String id) { return configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,id)); }
    @Override public AppConfig saveConfig(String id,AppConfig config) { config.setAppId(id); var existing=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,id)); if(existing!=null){config.setId(existing.getId());configMapper.updateById(config);}else{configMapper.insert(config);} return config; }
    @Override public Map<String,Object> run(String id,String query) { var r=new HashMap<String,Object>(); r.put("answer","(mock) Response to: "+query); r.put("sessionId",UUID.randomUUID().toString()); return r; }
}
