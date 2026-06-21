package com.fastrag.module.graph.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.graph.entity.KbEvaluation; import com.fastrag.module.graph.mapper.KbEvaluationMapper;
import com.fastrag.module.graph.service.EvaluationService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {
    private final KbEvaluationMapper mapper;
    @Override public List<KbEvaluation> list(String kbId) { return mapper.selectList(new LambdaQueryWrapper<KbEvaluation>().eq(KbEvaluation::getKbId,kbId).orderByDesc(KbEvaluation::getCreatedAt)); }
    @Override public KbEvaluation getDetail(String kbId,String id) { return mapper.selectById(id); }
    @Override public void run(String kbId,Map<String,Object> config) { var e=new KbEvaluation(); e.setKbId(kbId); e.setName((String)config.get("name")); e.setStatus("pending"); e.setAnswerModel((String)config.get("answerModel")); e.setJudgeModel((String)config.get("judgeModel")); mapper.insert(e); }
    @Override public void delete(String kbId,String id) { mapper.deleteById(id); }
}
