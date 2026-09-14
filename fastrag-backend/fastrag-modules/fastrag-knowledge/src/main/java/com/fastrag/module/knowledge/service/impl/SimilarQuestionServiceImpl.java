package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbSimilarQuestion; import com.fastrag.module.knowledge.mapper.KbSimilarQuestionMapper;
import com.fastrag.module.knowledge.service.SimilarQuestionService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class SimilarQuestionServiceImpl implements SimilarQuestionService {
    private final KbSimilarQuestionMapper mapper;
    @Override public List<KbSimilarQuestion> list(String kbId, String standardQuestionId) {
        var w=new LambdaQueryWrapper<KbSimilarQuestion>().eq(KbSimilarQuestion::getKbId,kbId);
        if(standardQuestionId!=null&&!standardQuestionId.isBlank()) w.eq(KbSimilarQuestion::getStandardQuestionId,standardQuestionId);
        return mapper.selectList(w.orderByDesc(KbSimilarQuestion::getSimilarity));
    }
    @Override public KbSimilarQuestion get(String id) { return mapper.selectById(id); }
    @Override public KbSimilarQuestion create(String kbId,KbSimilarQuestion q) {
        q.setId(null); q.setKbId(kbId); if(q.getEnabled()==null) q.setEnabled(1); if(q.getHitCount()==null) q.setHitCount(0); mapper.insert(q); return q;
    }
    @Override public KbSimilarQuestion update(String id,KbSimilarQuestion q) {
        var e=mapper.selectById(id); if(e==null) throw new RuntimeException("相似问法不存在"); q.setId(id); mapper.updateById(q); return q;
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
}
