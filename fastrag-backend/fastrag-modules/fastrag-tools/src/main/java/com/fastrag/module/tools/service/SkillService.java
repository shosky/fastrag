package com.fastrag.module.tools.service;
import com.fastrag.module.tools.entity.Skill; import java.util.*;
public interface SkillService { List<Skill> list(String keyword,String category); Skill get(String id); Skill create(Map<String,Object> form); Skill update(String id,Map<String,Object> form); void delete(String id); void toggleEnabled(String id); }
