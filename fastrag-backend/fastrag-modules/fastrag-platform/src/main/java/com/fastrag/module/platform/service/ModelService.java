package com.fastrag.module.platform.service;
import com.fastrag.module.platform.entity.*; import java.util.*;
public interface ModelService { List<ModelRecord> list(String keyword,String purpose); ModelRecord get(String id); ModelRecord create(Map<String,Object> form); ModelRecord update(String id,Map<String,Object> form); void delete(String id); }
