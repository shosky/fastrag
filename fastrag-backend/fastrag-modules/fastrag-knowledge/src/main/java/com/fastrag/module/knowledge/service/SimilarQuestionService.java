package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbSimilarQuestion; import java.util.*;
public interface SimilarQuestionService {
    List<KbSimilarQuestion> list(String kbId, String standardQuestionId);
    KbSimilarQuestion get(String id);
    KbSimilarQuestion create(String kbId, KbSimilarQuestion q);
    KbSimilarQuestion update(String id, KbSimilarQuestion q);
    void delete(String id);
}
