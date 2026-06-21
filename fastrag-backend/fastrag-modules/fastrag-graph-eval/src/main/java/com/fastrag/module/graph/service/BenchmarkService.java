package com.fastrag.module.graph.service;
import com.fastrag.module.graph.entity.KbBenchmark; import java.util.*;
public interface BenchmarkService { List<KbBenchmark> list(String kbId); KbBenchmark getDetail(String kbId,String id); KbBenchmark create(String kbId,Map<String,Object> form); void generate(String kbId,Map<String,Object> config); void delete(String kbId,String id); }
