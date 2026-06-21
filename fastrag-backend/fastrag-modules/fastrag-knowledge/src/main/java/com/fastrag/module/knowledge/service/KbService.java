package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.model.*; import java.util.*;
public interface KbService { Map<String,Object> list(String keyword,String category,int page,int pageSize); KbDto get(String id); KbDto create(KbCreateRequest req,String creator); KbDto update(String id,KbCreateRequest req); void delete(String id); List<Map<String,Object>> getCategories(); }
