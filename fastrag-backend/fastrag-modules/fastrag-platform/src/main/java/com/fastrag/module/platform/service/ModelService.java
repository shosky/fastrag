package com.fastrag.module.platform.service;
import com.fastrag.module.platform.entity.*; import java.util.*;
public interface ModelService {
    List<ModelRecord> list(String keyword,String purpose); ModelRecord get(String id);
    ModelRecord create(Map<String,Object> form); ModelRecord update(String id,Map<String,Object> form);
    void delete(String id);
    void toggle(String id);
    List<ModelRecord> importModels(List<Map<String,Object>> models);
    // 模型预置
    List<Map<String,Object>> listPresets();
    Map<String,Object> createPreset(Map<String,Object> preset);
    Map<String,Object> updatePreset(String id, Map<String,Object> preset);
    void deletePreset(String id);
}
