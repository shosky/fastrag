package com.fastrag.module.graph.service;

import com.fastrag.module.graph.entity.KbBenchmark;
import com.fastrag.module.graph.entity.KbBenchmarkQuestion;
import com.fastrag.module.graph.model.BenchmarkConfig;
import com.fastrag.module.graph.model.BenchmarkCreateForm;

import java.util.List;

public interface BenchmarkService {
    List<KbBenchmark> list(String kbId);

    KbBenchmark getDetail(String kbId, String id);

    List<KbBenchmarkQuestion> listQuestions(String kbId, String benchmarkId);

    KbBenchmark create(String kbId, BenchmarkCreateForm form);

    KbBenchmark generate(String kbId, BenchmarkConfig config);

    void delete(String kbId, String id);
}
