package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbStandardQuestion; import java.util.*;
public interface StandardQuestionService {
    List<KbStandardQuestion> list(String kbId, String category);
    KbStandardQuestion get(String id);
    KbStandardQuestion create(String kbId, KbStandardQuestion q);
    KbStandardQuestion update(String id, KbStandardQuestion q);
    void delete(String id);
    // 推荐相似问法：按 keyword 在相似问法库中模糊匹配，返回 top N
    List<Map<String,Object>> recommendSimilar(String kbId, String standardQuestionId, String keyword, int limit);
}
