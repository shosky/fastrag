package com.fastrag.module.graph.service;

import com.fastrag.module.graph.entity.KbEvaluation;
import com.fastrag.module.graph.model.EvaluationConfig;

import java.util.List;

public interface EvaluationService {
    List<KbEvaluation> list(String kbId);

    KbEvaluation getDetail(String kbId, String id);

    KbEvaluation run(String kbId, EvaluationConfig config);

    void delete(String kbId, String id);
}
