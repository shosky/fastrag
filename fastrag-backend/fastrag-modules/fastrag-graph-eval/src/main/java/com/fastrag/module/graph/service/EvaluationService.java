package com.fastrag.module.graph.service;
import com.fastrag.module.graph.entity.KbEvaluation; import java.util.*;
public interface EvaluationService { List<KbEvaluation> list(String kbId); KbEvaluation getDetail(String kbId,String id); void run(String kbId,Map<String,Object> config); void delete(String kbId,String id); }
