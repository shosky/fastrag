package com.fastrag.module.knowledge.controller;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbKnowledge;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.entity.KbTagRelation;
import com.fastrag.module.knowledge.mapper.KbKnowledgeMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.mapper.KbTagRelationMapper;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

/** 知识关联推荐：按 分类一致 + 标签共享 + 文本词元重合 打分，推荐相关知识与关联问答 */
@RestController @RequestMapping("/api/kb/{kbId}") @RequiredArgsConstructor
public class RelatedKnowledgeController {
    private final KbKnowledgeMapper knowledgeMapper;
    private final KbQaPairMapper qaPairMapper;
    private final KbTagRelationMapper tagRelationMapper;

    @GetMapping("/knowledge/{id}/related")
    public ApiResponse<?> related(@PathVariable String kbId,@PathVariable String id,@RequestParam(defaultValue="10") int limit) {
        KbKnowledge src = knowledgeMapper.selectById(id);
        if (src == null) throw new RuntimeException("知识条目不存在");
        Set<String> srcTags = tagIdsOf(id);
        Set<String> srcTokens = tokenize(src.getTitle() + " " + orEmpty(src.getContent()) + " " + orEmpty(src.getSummary()));

        List<Map<String,Object>> related = new ArrayList<>();
        for (KbKnowledge k : knowledgeMapper.selectList(new LambdaQueryWrapper<KbKnowledge>()
                .eq(KbKnowledge::getKbId,kbId).isNull(KbKnowledge::getDeletedAt).ne(KbKnowledge::getId,id))) {
            double score = 0.0; List<String> reasons = new ArrayList<>();
            Set<String> candTokens = tokenize(orEmpty(k.getTitle()) + " " + orEmpty(k.getContent()));
            double overlap = coverage(srcTokens, candTokens);
            score += overlap * 0.6;
            if (overlap > 0.05) reasons.add("内容相关");
            if (src.getCategory() != null && src.getCategory().equals(k.getCategory())) { score += 0.15; reasons.add("同类目：" + src.getCategory()); }
            Set<String> candTags = tagIdsOf(k.getId());
            long shared = srcTags.stream().filter(candTags::contains).count();
            if (shared > 0) { score += Math.min(0.3, shared * 0.1); reasons.add("共享标签"); }
            if (score >= 0.12) {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id",k.getId()); m.put("title",k.getTitle()); m.put("category",k.getCategory()); m.put("summary",k.getSummary());
                m.put("score",Math.round(Math.min(1.0, score) * 100) / 100.0); m.put("reasons",reasons);
                related.add(m);
            }
        }
        related.sort(Comparator.comparingDouble(m -> -((Double) m.get("score"))));
        related = related.stream().limit(limit).collect(Collectors.toList());

        // 关联问答：问题或关键词与源知识标题重合的 QA 对
        List<Map<String,Object>> qaRelated = new ArrayList<>();
        for (KbQaPair q : qaPairMapper.selectList(new LambdaQueryWrapper<KbQaPair>().eq(KbQaPair::getKbId,kbId))) {
            boolean hit = (q.getQuestion() != null && src.getTitle() != null &&
                    (q.getQuestion().contains(src.getTitle()) || src.getTitle().contains(q.getQuestion())))
                    || (q.getKeywords() != null && src.getTitle() != null && Arrays.stream(q.getKeywords().split("[,，]")).anyMatch(kw -> !kw.isBlank() && src.getTitle().contains(kw.trim())));
            if (hit) {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id",q.getId()); m.put("question",q.getQuestion()); m.put("answer",q.getAnswer()); m.put("faqType",q.getFaqType());
                qaRelated.add(m);
                if (qaRelated.size() >= 5) break;
            }
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("relatedKnowledge",related); out.put("relatedQaPairs",qaRelated);
        return ApiResponse.success(out);
    }

    private Set<String> tagIdsOf(String targetId) {
        return tagRelationMapper.selectList(new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTargetId,targetId))
                .stream().map(KbTagRelation::getTagId).collect(Collectors.toSet());
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        Set<String> tokens = new HashSet<>();
        StringBuilder cjk = new StringBuilder();
        for (String w : text.split("[\\s，。；！？,.;!?、()（）\\[\\]]+")) {
            if (w.matches("[a-zA-Z0-9]+")) { if (w.length() >= 2) tokens.add(w.toLowerCase()); }
            else {
                for (char c : w.toCharArray()) {
                    if (c >= 0x4E00 && c <= 0x9FFF) cjk.append(c);
                    else if (cjk.length() > 0) { flush(cjk, tokens); cjk.setLength(0); }
                }
                flush(cjk, tokens); cjk.setLength(0);
            }
        }
        return tokens;
    }
    private void flush(StringBuilder cjk,Set<String> tokens) { String s=cjk.toString(); if(s.length()==1)tokens.add(s); for(int i=0;i+2<=s.length();i++)tokens.add(s.substring(i,i+2)); }
    private double coverage(Set<String> a,Set<String> b) { if(a.isEmpty()||b.isEmpty())return 0.0; long hit=a.stream().filter(b::contains).count(); return (double) hit/Math.min(a.size(),20); }
    private String orEmpty(String s) { return s == null ? "" : s; }
}
