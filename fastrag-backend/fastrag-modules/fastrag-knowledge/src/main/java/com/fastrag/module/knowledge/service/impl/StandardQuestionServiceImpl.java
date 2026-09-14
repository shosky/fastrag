package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbSimilarQuestion; import com.fastrag.module.knowledge.entity.KbStandardQuestion;
import com.fastrag.module.knowledge.mapper.KbSimilarQuestionMapper; import com.fastrag.module.knowledge.mapper.KbStandardQuestionMapper;
import com.fastrag.module.knowledge.service.StandardQuestionService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class StandardQuestionServiceImpl implements StandardQuestionService {
    private final KbStandardQuestionMapper qMapper;
    private final KbSimilarQuestionMapper sMapper;
    @Override public List<KbStandardQuestion> list(String kbId, String category) {
        var w=new LambdaQueryWrapper<KbStandardQuestion>().eq(KbStandardQuestion::getKbId,kbId);
        if(category!=null&&!category.isBlank()) w.like(KbStandardQuestion::getCategory,category);
        return qMapper.selectList(w.orderByDesc(KbStandardQuestion::getHitCount));
    }
    @Override public KbStandardQuestion get(String id) { return qMapper.selectById(id); }
    @Override public KbStandardQuestion create(String kbId,KbStandardQuestion q) {
        q.setId(null); q.setKbId(kbId); if(q.getEnabled()==null) q.setEnabled(1); if(q.getHitCount()==null) q.setHitCount(0); qMapper.insert(q); return q;
    }
    @Override public KbStandardQuestion update(String id,KbStandardQuestion q) {
        var e=qMapper.selectById(id); if(e==null) throw new RuntimeException("标准问法不存在"); q.setId(id); qMapper.updateById(q); return q;
    }
    @Override public void delete(String id) {
        sMapper.delete(new LambdaQueryWrapper<KbSimilarQuestion>().eq(KbSimilarQuestion::getStandardQuestionId,id));
        qMapper.deleteById(id);
    }
    // bigram 余弦相似度：计算两个字符串的相似度分数 [0,1]
    private static double bigramCosine(String a, String b) {
        a = a == null ? "" : a.replaceAll("\\s+", "");
        b = b == null ? "" : b.replaceAll("\\s+", "");
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1.0;
        Map<String, int[]> freq = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) freq.computeIfAbsent(a.substring(i, i + 2), k -> new int[2])[0]++;
        for (int i = 0; i < b.length() - 1; i++) freq.computeIfAbsent(b.substring(i, i + 2), k -> new int[2])[1]++;
        double dot = 0, na = 0, nb = 0;
        for (var e : freq.values()) {
            dot += e[0] * (double) e[1];
            na += e[0] * (double) e[0];
            nb += e[1] * (double) e[1];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
    // 推荐相似问法：模糊匹配 keyword，取前 limit 条，并附加 bigram 相似度评分
    @Override public List<Map<String,Object>> recommendSimilar(String kbId, String standardQuestionId, String keyword, int limit) {
        var w=new LambdaQueryWrapper<KbSimilarQuestion>().eq(KbSimilarQuestion::getKbId,kbId).eq(KbSimilarQuestion::getStandardQuestionId,standardQuestionId).like(KbSimilarQuestion::getQuestion,keyword).last("LIMIT "+limit);
        var list=sMapper.selectList(w);
        List<Map<String,Object>> r=new ArrayList<>();
        for(var s:list) {
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("id",s.getId()); m.put("question",s.getQuestion());
            // 用 bigram 余弦相似度计算与标准问法的相似度
            var std = qMapper.selectById(standardQuestionId);
            double sim = 0;
            if (std != null && std.getStandardQuestion() != null) {
                sim = bigramCosine(std.getStandardQuestion(), s.getQuestion());
            }
            m.put("similarity", Math.round(sim * 100) / 100.0);
            m.put("hitCount",s.getHitCount());
            r.add(m);
        }
        return r;
    }
}
