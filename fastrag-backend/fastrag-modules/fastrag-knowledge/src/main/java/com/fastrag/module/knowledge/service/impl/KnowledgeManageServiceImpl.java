package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.KnowledgeManageService;
import com.fastrag.module.knowledge.service.KnowledgeTicketService;
import com.fastrag.security.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Slf4j @Service @RequiredArgsConstructor
public class KnowledgeManageServiceImpl implements KnowledgeManageService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final KbKnowledgeMapper knowledgeMapper; private final KbKnowledgeTestMapper testMapper; private final KbKnowledgeDialogMapper dialogMapper;
    private final KbAttributeDefMapper attrDefMapper;
    private final KnowledgeTicketService ticketService;
    @Override public List<KbKnowledge> list(String kbId,String keyword,String category,String attrDefId,String attrValue) {
        var w=new LambdaQueryWrapper<KbKnowledge>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledge::getKbId,kbId);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbKnowledge::getTitle,keyword);
        if(category!=null&&!category.isEmpty()) w.eq(KbKnowledge::getCategory,category);
        // 回收站中的条目不在正常列表出现
        w.isNull(KbKnowledge::getDeletedAt);
        var list=knowledgeMapper.selectList(w.orderByDesc(KbKnowledge::getCreatedAt));
        // 按自定义属性筛选：attributes 为 TEXT 存 JSON，条目量级为单库级别，Java 侧过滤（避免依赖 SQL JSON 函数解析脏数据）
        if(attrDefId!=null&&!attrDefId.isBlank()) list=list.stream().filter(k->matchesAttr(k,attrDefId,attrValue)).toList();
        return list;
    }
    private boolean matchesAttr(KbKnowledge k,String attrDefId,String attrValue) {
        Object v=parseAttrValues(k.getAttributes()).get(attrDefId);
        String sv=v==null?null:String.valueOf(v).trim();
        boolean has=sv!=null&&!sv.isEmpty();
        if(attrValue==null||attrValue.isBlank()) return has;                 // 只筛"有该属性值"的条目
        if("__none__".equals(attrValue)) return !has;                        // 筛"未填该属性"的条目
        return attrValue.equals(sv);
    }
    /** 按知识属性分组统计（依据知识属性进行分组）：返回各属性值分组及条目数，select 类型把定义中配置但暂无数据的选项也列出（计数 0） */
    @Override public Map<String,Object> attributeGroups(String kbId,String attrDefId) {
        var def=attrDefId==null?null:attrDefMapper.selectById(attrDefId);
        var items=knowledgeMapper.selectList(new LambdaQueryWrapper<KbKnowledge>()
            .eq(kbId!=null&&!kbId.isEmpty(),KbKnowledge::getKbId,kbId).isNull(KbKnowledge::getDeletedAt));
        Map<String,Integer> counts=new LinkedHashMap<>();
        int ungrouped=0;
        for(var k:items) {
            Object v=parseAttrValues(k.getAttributes()).get(attrDefId);
            String sv=v==null?null:String.valueOf(v).trim();
            if(sv==null||sv.isEmpty()) { ungrouped++; continue; }
            counts.merge(sv,1,Integer::sum);
        }
        if(def!=null&&"select".equals(def.getAttrType())) for(var o:selectOptions(def)) counts.putIfAbsent(o,0);
        List<Map<String,Object>> groups=new ArrayList<>();
        counts.entrySet().stream().sorted((a,b)->Integer.compare(b.getValue(),a.getValue())).forEach(e->{
            Map<String,Object> m=new LinkedHashMap<>(); m.put("value",e.getKey()); m.put("count",e.getValue()); groups.add(m);
        });
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("kbId",kbId); r.put("attrDefId",attrDefId);
        r.put("attrName",def==null?null:def.getName()); r.put("attrType",def==null?null:def.getAttrType());
        r.put("total",items.size()); r.put("ungrouped",ungrouped); r.put("groups",groups);
        return r;
    }
    /**
     * 属性值校验与归一化：仅当请求携带 attributes 字段时执行（未携带则完全跳过，保证既有调用方不受影响）。
     * 规则：必填项非空、number 为数字、date 为 yyyy-MM-dd、select 取值须在选项内；未知属性定义的键一并剔除。
     */
    private void validateAndNormalizeAttributes(KbKnowledge k) {
        String raw=k.getAttributes();
        if(raw==null) return;
        var defs=attrDefMapper.selectList(new LambdaQueryWrapper<KbAttributeDef>().eq(KbAttributeDef::getKbId,k.getKbId()));
        Map<String,Object> values=parseAttrValues(raw);
        Map<String,Object> clean=new LinkedHashMap<>();
        for(var d:defs) {
            Object v=values.get(d.getId());
            boolean empty=v==null||String.valueOf(v).trim().isEmpty();
            if(empty) {
                if(Integer.valueOf(1).equals(d.getRequired())) throw BusinessException.badRequest("属性「"+d.getName()+"」为必填项");
                continue;
            }
            String sv=String.valueOf(v).trim();
            switch(d.getAttrType()==null?"text":d.getAttrType()) {
                case "number" -> { try { new java.math.BigDecimal(sv); } catch(NumberFormatException e) { throw BusinessException.badRequest("属性「"+d.getName()+"」应为数字"); } }
                case "date" -> { try { java.time.LocalDate.parse(sv); } catch(Exception e) { throw BusinessException.badRequest("属性「"+d.getName()+"」应为日期(yyyy-MM-dd)"); } }
                case "select" -> { if(!selectOptions(d).contains(sv)) throw BusinessException.badRequest("属性「"+d.getName()+"」取值不在选项范围内："+sv); }
                default -> { }
            }
            clean.put(d.getId(),sv);
        }
        k.setAttributes(clean.isEmpty()?null:toJson(clean));
    }
    private Map<String,Object> parseAttrValues(String raw) {
        Map<String,Object> m=new LinkedHashMap<>();
        if(raw==null||raw.isBlank()) return m;
        try { var node=MAPPER.readTree(raw); if(node.isObject()) node.fields().forEachRemaining(e->m.put(e.getKey(),e.getValue().asText())); }
        catch (Exception e) { /* 非法 JSON 视为无属性值，后续按必填规则给出提示 */ }
        return m;
    }
    private List<String> selectOptions(KbAttributeDef d) {
        if(d.getOptions() instanceof List<?> l) return l.stream().map(String::valueOf).toList();
        return List.of();
    }
    private static String toJson(Object v) { try { return MAPPER.writeValueAsString(v); } catch (Exception e) { return null; } }
    @Override public KbKnowledge get(String id) {
        var k=knowledgeMapper.selectById(id);
        if(k!=null){ k.setViewCount((k.getViewCount()!=null?k.getViewCount():0)+1); knowledgeMapper.updateById(k); }
        return k;
    }
    @Override public KbKnowledge create(KbKnowledge knowledge) {
        if(knowledge.getStatus()==null) knowledge.setStatus("draft");
        if(knowledge.getVersion()==null) knowledge.setVersion(1);
        if(knowledge.getSource()==null) knowledge.setSource("manual");
        if(knowledge.getViewCount()==null) knowledge.setViewCount(0);
        validateAndNormalizeAttributes(knowledge);
        knowledgeMapper.insert(knowledge);
        // 以「待审核」状态录入即视为发起新增流程：自动生成知识工单等待审批
        if("reviewing".equals(knowledge.getStatus())) autoTicket(knowledge,"create");
        return knowledge;
    }
    @Override public KbKnowledge update(String id,KbKnowledge knowledge) {
        var old=knowledgeMapper.selectById(id);
        // attributes 为 null 时既不校验也不落库（MP 默认跳过 null 字段），保留原属性值；携带则校验并归一化
        validateAndNormalizeAttributes(knowledge);
        knowledge.setId(id); knowledgeMapper.updateById(knowledge);
        var cur=knowledgeMapper.selectById(id);
        // 状态流转到「待审核」= 发起修改流程：自动生成知识工单（同一状态不重复生成）
        if(cur!=null&&"reviewing".equals(cur.getStatus())&&(old==null||!"reviewing".equals(old.getStatus()))) autoTicket(cur,"update");
        return cur;
    }
    /** 发起新增/修改流程后自动生成知识工单；失败只告警不影响主流程 */
    private void autoTicket(KbKnowledge k,String type) {
        try {
            ticketService.autoCreate(k.getKbId(),k.getId(),type,
                "知识"+("create".equals(type)?"新增":"修改")+"待审："+(k.getTitle()==null?"":k.getTitle()),
                "状态流转至待审核，等待审批",
                currentOperator(k.getCreatedBy()));
        } catch (Exception e) { log.warn("生成知识工单失败: {}",e.getMessage()); }
    }
    /** 当前操作人：取登录用户名，无上下文（定时/脚本）时回退传入值或 system */
    private static String currentOperator(String fallback) {
        try {
            var u=SecurityUtil.getCurrentUser();
            if(u!=null&&u.getUsername()!=null&&!u.getUsername().isBlank()) return u.getUsername();
        } catch (Exception ignore) { }
        return fallback==null||fallback.isBlank()?"system":fallback;
    }
    // 删除为软删：置 deleted_at，可从回收站恢复
    @Override public void delete(String id) {
        var k=knowledgeMapper.selectById(id);
        if(k!=null){ k.setDeletedAt(LocalDateTime.now()); knowledgeMapper.updateById(k); }
    }
    // 回收站：聚合三类来源——用户删除(deleted)、取消发布(offline)、到期失效(expired)，集中管理与再利用
    private static final List<String> RECYCLE_STATUSES = List.of("offline","expired");
    @Override public List<KbKnowledge> listDeleted(String kbId) {
        var w=new LambdaQueryWrapper<KbKnowledge>()
            .and(q->q.isNotNull(KbKnowledge::getDeletedAt).or().in(KbKnowledge::getStatus,RECYCLE_STATUSES));
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledge::getKbId,kbId);
        var list=knowledgeMapper.selectList(w.orderByDesc(KbKnowledge::getUpdatedAt));
        for(var k:list){
            if(k.getDeletedAt()!=null){ k.setRecycleSource("deleted"); k.setRecycleTime(k.getDeletedAt()); }
            else { k.setRecycleSource("offline".equals(k.getStatus())?"offline":"expired"); k.setRecycleTime(k.getUpdatedAt()); }
        }
        return list;
    }
    @Override public void restore(String id) {
        var k=knowledgeMapper.selectById(id);
        if(k==null) return;
        if(k.getDeletedAt()!=null){
            // updateById 默认忽略 null 字段，恢复必须用 UpdateWrapper 显式置 null
            knowledgeMapper.update(null,new LambdaUpdateWrapper<KbKnowledge>().eq(KbKnowledge::getId,id).set(KbKnowledge::getDeletedAt,null));
        } else {
            // 取消发布/失效的条目恢复为草稿，修正后可重新发布
            knowledgeMapper.update(null,new LambdaUpdateWrapper<KbKnowledge>().eq(KbKnowledge::getId,id).set(KbKnowledge::getStatus,"draft"));
        }
    }
    // 失效扫描：valid_until 已到期的条目置为 expired（进入回收站「失效」来源），返回处理条数
    @Override public int expireScan(String kbId) {
        var w=new LambdaQueryWrapper<KbKnowledge>()
            .isNotNull(KbKnowledge::getValidUntil).lt(KbKnowledge::getValidUntil,LocalDateTime.now())
            .ne(KbKnowledge::getStatus,"expired").isNull(KbKnowledge::getDeletedAt);
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledge::getKbId,kbId);
        var list=knowledgeMapper.selectList(w);
        for(var k:list){
            k.setStatus("expired"); knowledgeMapper.updateById(k);
            log.info("知识条目失效: {} {}",k.getId(),k.getTitle());
        }
        return list.size();
    }
    /** 每日 01:30 扫描到期知识（与 KbSyncServiceImpl 的定时模式一致）；演示/运维可用 POST /knowledge/expire-scan 手动触发 */
    @Scheduled(cron = "0 30 1 * * ?")
    public void scheduleExpireScan() {
        try { int n=expireScan(null); if(n>0) log.info("知识失效扫描完成，处理 {} 条",n); }
        catch (Exception e) { log.warn("知识失效扫描异常",e); }
    }
    @Override public void permanentDelete(String id) { knowledgeMapper.deleteById(id); }
    @Override public void emptyRecycleBin(String kbId) {
        knowledgeMapper.delete(new LambdaQueryWrapper<KbKnowledge>().isNotNull(KbKnowledge::getDeletedAt).eq(KbKnowledge::getKbId,kbId));
    }
    @Override public void updateCoverImage(String id, String objectKey) {
        var k = new KbKnowledge();
        k.setId(id);
        k.setCoverImage(objectKey);
        knowledgeMapper.updateById(k);
    }
    @Override public String getContent(String id) {
        var k = knowledgeMapper.selectById(id);
        return k == null ? null : k.getContent();
    }
    @Override public List<KbKnowledgeTest> listTests(String kbId,String knowledgeId) {
        var w=new LambdaQueryWrapper<KbKnowledgeTest>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeTest::getKbId,kbId);
        if(knowledgeId!=null&&!knowledgeId.isEmpty()) w.eq(KbKnowledgeTest::getKnowledgeId,knowledgeId);
        return testMapper.selectList(w.orderByDesc(KbKnowledgeTest::getCreatedAt));
    }
    @Override public KbKnowledgeTest createTest(KbKnowledgeTest test) { testMapper.insert(test); return test; }
    @Override public KbKnowledgeTest updateTest(String id,KbKnowledgeTest test) {
        test.setId(id); testMapper.updateById(test); return testMapper.selectById(id);
    }
    @Override public void deleteTest(String id) { testMapper.deleteById(id); }
    @Override public List<KbKnowledgeDialog> listDialogs(String kbId,String knowledgeId) {
        var w=new LambdaQueryWrapper<KbKnowledgeDialog>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeDialog::getKbId,kbId);
        if(knowledgeId!=null&&!knowledgeId.isEmpty()) w.eq(KbKnowledgeDialog::getKnowledgeId,knowledgeId);
        return dialogMapper.selectList(w.orderByDesc(KbKnowledgeDialog::getCreatedAt));
    }
    @Override public KbKnowledgeDialog createDialog(KbKnowledgeDialog dialog) { dialogMapper.insert(dialog); return dialog; }
    @Override public void deleteDialog(String id) { dialogMapper.deleteById(id); }
    @Override public Map<String,Object> judge(String id,String query,String model) {
        var dialog=dialogMapper.selectById(id);
        Map<String,Object> result=new LinkedHashMap<>();
        double confidence=0.85+new Random().nextInt(15)/100.0;
        String matched=confidence>0.8?"matched":(confidence>0.5?"partial":"unmatched");
        result.put("id",id); result.put("query",query); result.put("result",matched); result.put("confidence",Math.round(confidence*100.0)/100.0);
        if(dialog!=null){ dialog.setResult(matched); dialog.setConfidence(confidence); dialogMapper.updateById(dialog); }
        return result;
    }
}
