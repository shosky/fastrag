package com.fastrag.module.application.service;
import com.fastrag.module.application.entity.*; import java.util.*;
public interface AppService { List<App> list(String keyword,String tag); App get(String id); App create(Map<String,Object> form); App update(String id,Map<String,Object> form); void delete(String id); List<AppTemplate> getTemplates(); AppConfig getConfig(String id); AppConfig saveConfig(String id,AppConfig config); Map<String,Object> run(String id,String query); }
