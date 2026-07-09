package com.fastrag.module.platform.service;
import com.fastrag.module.platform.entity.*; import java.util.*;
public interface ModelService {
    List<ModelRecord> list(String keyword,String purpose); ModelRecord get(String id);
    ModelRecord create(Map<String,Object> form); ModelRecord update(String id,Map<String,Object> form);
    void delete(String id);
    void toggle(String id);
    List<ModelRecord> importModels(List<Map<String,Object>> models);
    // 模型测试
    Map<String,Object> testChat(String modelId, String prompt);
    Map<String,Object> testEmbedding(String modelId, String text);
    Map<String,Object> testRerank(String modelId, String query, List<String> documents);
    Map<String,Object> testModel(String modelId, String purpose, Map<String,Object> params);
    // 模型预置
    List<Map<String,Object>> listPresets();
    Map<String,Object> createPreset(Map<String,Object> preset);
    Map<String,Object> updatePreset(String id, Map<String,Object> preset);
    void deletePreset(String id);
}
