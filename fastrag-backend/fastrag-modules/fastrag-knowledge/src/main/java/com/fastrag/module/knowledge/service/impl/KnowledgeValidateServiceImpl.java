package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbKnowledge; import com.fastrag.module.knowledge.entity.KbKnowledgeValidate;
import com.fastrag.module.knowledge.mapper.KbKnowledgeMapper; import com.fastrag.module.knowledge.mapper.KbKnowledgeValidateMapper;
import com.fastrag.module.knowledge.service.KnowledgeValidateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.time.temporal.ChronoUnit; import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgeValidateServiceImpl implements KnowledgeValidateService {
    private final KbKnowledgeValidateMapper mapper;
    private final KbKnowledgeMapper knowledgeMapper;
    private final ObjectMapper objectMapper;
    // 重复/一致性比对用的文本采样长度，控制 O(n²) 比对开销；问题明细最多记录条数
    private static final int SAMPLE_LEN = 400;
    private static final int MAX_ISSUES = 100;
    @Override public List<KbKnowledgeValidate> list(String kbId) {
        var w=new LambdaQueryWrapper<KbKnowledgeValidate>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeValidate::getKbId,kbId);
        return mapper.selectList(w.orderByDesc(KbKnowledgeValidate::getCreatedAt));
    }
    @Override public KbKnowledgeValidate get(String id) { return mapper.selectById(id); }
    // 真实校验：读取库内知识条目，按类型做重复/过期/质量/一致性判定
    @Override public KbKnowledgeValidate check(KbKnowledgeValidate v) {
        // 前端将 similarityThreshold / expiredDays 放在 result JSON 中传入，先解析再覆写
        double threshold=0.85; int expiredDays=365;
        try { if(v.getResult()!=null&&!v.getResult().isBlank()) {
            var n=objectMapper.readTree(v.getResult());
            if(n.has("similarityThreshold")) threshold=n.get("similarityThreshold").asDouble(0.85);
            if(n.has("expiredDays")) expiredDays=n.get("expiredDays").asInt(365);
        } } catch (Exception ignore) { }
        if(v.getTargetValue()!=null&&!v.getTargetValue().isBlank()) {
            try { expiredDays=Integer.parseInt(v.getTargetValue().trim()); } catch (NumberFormatException ignore) { }
        }
        var items=knowledgeMapper.selectList(new LambdaQueryWrapper<KbKnowledge>()
            .eq(KbKnowledge::getKbId,v.getKbId()).isNull(KbKnowledge::getDeletedAt));
        String type=v.getValidateType()==null?"duplicate":v.getValidateType();
        List<Map<String,Object>> issues=new ArrayList<>();
        int failed=0,warning=0;
        switch(type){
            case "expired" -> { int[] c=expiredCheck(items,expiredDays,issues); failed=c[0]; warning=c[1]; }
            case "quality" -> { int[] c=qualityCheck(items,issues); failed=c[0]; warning=c[1]; }
            case "consistency" -> { int[] c=consistencyCheck(items,issues); failed=c[0]; warning=c[1]; }
            default -> { int[] c=duplicateCheck(items,threshold,issues); failed=c[0]; warning=c[1]; }
        }
        int total=items.size();
        v.setStatus("completed");
        v.setTotalCount(total); v.setFailedCount(failed); v.setWarningCount(warning); v.setPassedCount(Math.max(0,total-failed-warning));
        Map<String,Object> detail=new LinkedHashMap<>();
        detail.put("validateType",type);
        if("duplicate".equals(type)) detail.put("similarityThreshold",threshold);
        if("expired".equals(type)) detail.put("expiredDays",expiredDays);
        detail.put("issueCount",issues.size());
        detail.put("issues",issues.size()>MAX_ISSUES?issues.subList(0,MAX_ISSUES):issues);
        try { v.setResult(objectMapper.writeValueAsString(detail)); }
        catch (Exception e) { v.setResult("{\"error\":\"serialization failed\"}"); }
        v.setCompletedAt(LocalDateTime.now());
        mapper.insert(v); return v;
    }
    // 重复检查：标题+内容采样的字符 bigram 余弦相似度，>=阈值为重复(失败)，>=阈值-0.15 为疑似(警告)
    private int[] duplicateCheck(List<KbKnowledge> items,double threshold,List<Map<String,Object>> issues) {
        int failed=0,warning=0; Set<String> failedIds=new HashSet<>(),warnIds=new HashSet<>();
        for(int i=0;i<items.size();i++) for(int j=i+1;j<items.size();j++) {
            double sim=similarity(sample(items.get(i)),sample(items.get(j)));
            if(sim>=threshold) {
                failed+=mark(issues,failedIds,items.get(i),"与「"+items.get(j).getTitle()+"」内容重复（相似度"+pct(sim)+"）");
                failed+=mark(issues,failedIds,items.get(j),"与「"+items.get(i).getTitle()+"」内容重复（相似度"+pct(sim)+"）");
            } else if(sim>=threshold-0.15) {
                warning+=mark(issues,warnIds,items.get(i),"与「"+items.get(j).getTitle()+"」疑似重复（相似度"+pct(sim)+"）");
                warning+=mark(issues,warnIds,items.get(j),"与「"+items.get(i).getTitle()+"」疑似重复（相似度"+pct(sim)+"）");
            }
        }
        return new int[]{failed,warning};
    }
    // 过期检查：归档即失败；updatedAt 超过 expiredDays 失败，超过 80% 警告
    private int[] expiredCheck(List<KbKnowledge> items,int expiredDays,List<Map<String,Object>> issues) {
        int failed=0,warning=0; var now=LocalDateTime.now();
        for(var k:items) {
            if("archived".equals(k.getStatus())) { failed+=mark(issues,null,k,"条目已归档"); continue; }
            var ref=k.getUpdatedAt()!=null?k.getUpdatedAt():k.getCreatedAt();
            if(ref==null) continue;
            long days=ChronoUnit.DAYS.between(ref,now);
            if(days>expiredDays) { failed+=mark(issues,null,k,"已 "+days+" 天未更新（阈值 "+expiredDays+" 天）"); }
            else if(days>expiredDays*0.8) { warning+=mark(issues,null,k,"已 "+days+" 天未更新，接近过期（阈值 "+expiredDays+" 天）"); }
        }
        return new int[]{failed,warning};
    }
    // 质量检查：内容为空/过短失败，缺摘要警告
    private int[] qualityCheck(List<KbKnowledge> items,List<Map<String,Object>> issues) {
        int failed=0,warning=0;
        for(var k:items) {
            String content=k.getContent()==null?"":k.getContent().trim();
            if(content.isEmpty()) { failed+=mark(issues,null,k,"内容为空"); }
            else if(content.length()<50) { failed+=mark(issues,null,k,"内容过短（"+content.length()+" 字，少于 50 字）"); }
            else if(k.getSummary()==null||k.getSummary().isBlank()) { warning+=mark(issues,null,k,"缺少摘要"); }
        }
        return new int[]{failed,warning};
    }
    // 一致性检查：同标题（去空白后）内容不同→失败；标题内容完全相同→警告
    private int[] consistencyCheck(List<KbKnowledge> items,List<Map<String,Object>> issues) {
        int failed=0,warning=0;
        Map<String,List<KbKnowledge>> byTitle=new LinkedHashMap<>();
        for(var k:items) byTitle.computeIfAbsent(k.getTitle()==null?"":k.getTitle().replaceAll("\\s+",""),t->new ArrayList<>()).add(k);
        for(var group:byTitle.values()) {
            if(group.size()<2) continue;
            var first=group.get(0);
            for(int i=1;i<group.size();i++) {
                var other=group.get(i);
                boolean sameContent=Objects.equals(norm(first.getContent()),norm(other.getContent()));
                if(sameContent) warning+=mark(issues,null,other,"与「"+first.getTitle()+"」标题内容完全相同，疑似冗余条目");
                else failed+=mark(issues,null,other,"与「"+first.getTitle()+"」同标题但内容不一致，存在版本冲突");
            }
        }
        return new int[]{failed,warning};
    }
    private int mark(List<Map<String,Object>> issues,Set<String> dedupIds,KbKnowledge k,String reason) {
        if(dedupIds!=null&&!dedupIds.add(k.getId())) return 0; // 同一条目只记一次失败/警告
        if(issues.size()<MAX_ISSUES) {
            Map<String,Object> issue=new LinkedHashMap<>();
            issue.put("id",k.getId()); issue.put("title",k.getTitle()); issue.put("reason",reason);
            issues.add(issue);
        }
        return 1;
    }
    private static String sample(KbKnowledge k) { return (k.getTitle()==null?"":k.getTitle())+" "+(k.getContent()==null?"":k.getContent()); }
    private static String norm(String s) { return s==null?"":s.replaceAll("\\s+",""); }
    private static String pct(double v) { return Math.round(v*100)+"%"; }
    // 字符 bigram 词频向量的余弦相似度：纯 Java 实现，无外部服务依赖
    static double similarity(String a,String b) {
        a=a==null?"":a.replaceAll("\\s+",""); b=b==null?"":b.replaceAll("\\s+","");
        if(a.isEmpty()||b.isEmpty()) return 0;
        if(a.equals(b)) return 1.0;
        if(a.length()>SAMPLE_LEN) a=a.substring(0,SAMPLE_LEN);
        if(b.length()>SAMPLE_LEN) b=b.substring(0,SAMPLE_LEN);
        Map<String,int[]> freq=new HashMap<>();
        for(int i=0;i<a.length()-1;i++) freq.computeIfAbsent(a.substring(i,i+2),k->new int[2])[0]++;
        for(int i=0;i<b.length()-1;i++) freq.computeIfAbsent(b.substring(i,i+2),k->new int[2])[1]++;
        double dot=0,na=0,nb=0;
        for(var e:freq.values()){ dot+=e[0]*(double)e[1]; na+=e[0]*(double)e[0]; nb+=e[1]*(double)e[1]; }
        return na==0||nb==0?0:dot/(Math.sqrt(na)*Math.sqrt(nb));
    }
}
