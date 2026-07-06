package com.fastrag.module.tools.service;
import com.fastrag.module.tools.entity.Skill; import java.util.*;

public interface SkillService {
    List<Skill> list(String keyword, String category);
    List<Skill> listAccessible(String keyword, String category, String sourceType);
    List<Skill> listBuiltin();
    Skill get(String id);
    Skill getBySlug(String slug);
    Skill create(Map<String, Object> form);
    Skill update(String id, Map<String, Object> form);
    void delete(String id);
    void toggleEnabled(String id);
    boolean existsBySlug(String slug);
}
