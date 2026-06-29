package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbKnowledgeValidate; import com.fastrag.module.knowledge.mapper.KbKnowledgeValidateMapper;
import com.fastrag.module.knowledge.service.KnowledgeValidateService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgeValidateServiceImpl implements KnowledgeValidateService {
    private final KbKnowledgeValidateMapper mapper;
    @Override public List<KbKnowledgeValidate> list(String kbId) {
        var w=new LambdaQueryWrapper<KbKnowledgeValidate>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeValidate::getKbId,kbId);
        return mapper.selectList(w.orderByDesc(KbKnowledgeValidate::getCreatedAt));
    }
    @Override public KbKnowledgeValidate get(String id) { return mapper.selectById(id); }
    @Override public KbKnowledgeValidate check(KbKnowledgeValidate v) {
        if(v.getStatus()==null) v.setStatus("completed");
        if(v.getTotalCount()==null) v.setTotalCount(120);
        // 模拟校验结果
        int total=v.getTotalCount();
        int failed=switch(v.getValidateType()!=null?v.getValidateType():"duplicate"){
            case "duplicate" -> 8; case "expired" -> 5; case "quality" -> 12; case "consistency" -> 3; default -> 6;
        };
        int warning=failed/2;
        v.setFailedCount(failed); v.setWarningCount(warning); v.setPassedCount(total-failed-warning);
        Map<String,Object> detail=new LinkedHashMap<>();
        detail.put("duplicateGroups",v.getValidateType()!=null&&v.getValidateType().equals("duplicate")?failed:0);
        detail.put("expiredItems",v.getValidateType()!=null&&v.getValidateType().equals("expired")?failed:0);
        detail.put("qualityIssues",v.getValidateType()!=null&&v.getValidateType().equals("quality")?failed:0);
        v.setResult(detail.toString());
        v.setCompletedAt(LocalDateTime.now());
        mapper.insert(v); return v;
    }
}
