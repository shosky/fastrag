package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbKnowledge; import com.fastrag.module.knowledge.mapper.KbKnowledgeMapper;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.MatterKnowledgeRelService;
import com.fastrag.module.platform.service.ConfigManageService;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Slf4j @Service @RequiredArgsConstructor
public class MatterKnowledgeRelServiceImpl implements MatterKnowledgeRelService {
    private final KbMatterKnowledgeRelMapper mapper;
    private final KbKnowledgeMapper knowledgeMapper;
    private final KbMatterKnowledgeHitMapper hitMapper;
    private final ConfigManageService configService;
    // 窗口天数与返回条数的系统配置键（后台可配置，缺省 30 天 / 5 个）
    private static final String CFG_WINDOW_DAYS = "matter_hot_window_days";
    private static final String CFG_TOP_N = "matter_hot_top_n";
    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int DEFAULT_TOP_N = 5;
    @Override public List<KbMatterKnowledgeRel> list(String kbId,String matterName,String keyword) {
        var w=new LambdaQueryWrapper<KbMatterKnowledgeRel>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbMatterKnowledgeRel::getKbId,kbId);
        if(matterName!=null&&!matterName.isEmpty()) w.eq(KbMatterKnowledgeRel::getMatterName,matterName);
        if(keyword!=null&&!keyword.isEmpty()) w.and(q->q.like(KbMatterKnowledgeRel::getMatterName,keyword).or().like(KbMatterKnowledgeRel::getKnowledgeTitle,keyword));
        return mapper.selectList(w.orderByDesc(KbMatterKnowledgeRel::getCreatedAt));
    }
    @Override public KbMatterKnowledgeRel get(String id) { return mapper.selectById(id); }
    @Override public KbMatterKnowledgeRel create(String kbId,KbMatterKnowledgeRel rel) {
        rel.setId(null); rel.setKbId(kbId);
        if(rel.getRelationType()==null) rel.setRelationType("reference");
        fillKnowledgeTitle(rel);
        mapper.insert(rel); return rel;
    }
    @Override public KbMatterKnowledgeRel update(String id,KbMatterKnowledgeRel rel) {
        rel.setId(id); fillKnowledgeTitle(rel);
        mapper.updateById(rel); return mapper.selectById(id);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }

    // ===== 事项热点关联：从业务事项维度聚合热点知识点，供座席定位事项后参考 =====
    @Override public List<Map<String,Object>> matters(String kbId) {
        Map<String,Map<String,Object>> byCode=new LinkedHashMap<>();
        for(var rel:list(kbId,null,null)) {
            String code=rel.getMatterCode()!=null&&!rel.getMatterCode().isBlank()?rel.getMatterCode():rel.getMatterName();
            if(code==null||code.isBlank()) continue;
            var item=byCode.computeIfAbsent(code,k->{
                Map<String,Object> m=new LinkedHashMap<>();
                m.put("matterCode",code); m.put("matterName",rel.getMatterName()); m.put("knowledgeCount",0);
                return m;
            });
            item.put("knowledgeCount",((Number)item.get("knowledgeCount")).intValue()+1);
        }
        return new ArrayList<>(byCode.values());
    }
    @Override public Map<String,Object> hotKnowledge(String kbId,String matterCode,Integer days,Integer top) {
        int windowDays=days!=null&&days>0?days:configInt(CFG_WINDOW_DAYS,DEFAULT_WINDOW_DAYS);
        int topN=top!=null&&top>0?top:configInt(CFG_TOP_N,DEFAULT_TOP_N);
        LocalDateTime since=LocalDateTime.now().minusDays(windowDays);

        // 热度：窗口内该事项的命中次数（按知识点聚合）
        Map<String,Integer> hitCount=new LinkedHashMap<>();
        for(var h:hitMapper.selectList(new LambdaQueryWrapper<KbMatterKnowledgeHit>()
                .eq(KbMatterKnowledgeHit::getKbId,kbId)
                .and(q->q.eq(KbMatterKnowledgeHit::getMatterCode,matterCode).or().eq(KbMatterKnowledgeHit::getMatterName,matterCode))
                .ge(KbMatterKnowledgeHit::getCreatedAt,since))) {
            hitCount.merge(h.getKnowledgeId(),1,Integer::sum);
        }

        // 候选 = 显式关联事项的知识点 ∪ 窗口内命中过的知识点
        List<Map<String,Object>> items=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(var rel:mapper.selectList(new LambdaQueryWrapper<KbMatterKnowledgeRel>()
                .eq(KbMatterKnowledgeRel::getKbId,kbId)
                .and(q->q.eq(KbMatterKnowledgeRel::getMatterCode,matterCode).or().eq(KbMatterKnowledgeRel::getMatterName,matterCode)))) {
            String kid=rel.getKnowledgeId();
            if(kid==null||kid.isBlank()||!seen.add(kid)) continue;
            if(!isUsable(kid)) continue;
            items.add(buildItem(kbId,kid,rel.getKnowledgeTitle(),hitCount.getOrDefault(kid,0),true));
        }
        for(var e:hitCount.entrySet()) {
            if(!seen.add(e.getKey())) continue;
            var k=knowledgeMapper.selectById(e.getKey());
            if(k==null) continue;
            items.add(buildItem(kbId,e.getKey(),k.getTitle(),e.getValue(),false));
        }
        // 排序：命中次数降序；次数相同显式关联优先；再按标题稳定排序
        items.sort((a,b)->{
            int c=Integer.compare((int)b.get("hitCount"),(int)a.get("hitCount"));
            if(c!=0) return c;
            int r=Boolean.compare((boolean)b.get("fromRel"),(boolean)a.get("fromRel"));
            if(r!=0) return r;
            return String.valueOf(a.get("title")).compareTo(String.valueOf(b.get("title")));
        });
        List<Map<String,Object>> topItems=items.size()>topN?new ArrayList<>(items.subList(0,topN)):items;

        Map<String,Object> result=new LinkedHashMap<>();
        result.put("kbId",kbId); result.put("matterCode",matterCode);
        result.put("windowDays",windowDays); result.put("topN",topN);
        result.put("totalCandidates",items.size()); result.put("items",topItems);
        return result;
    }
    @Override public int recordHit(String kbId,String matterCode,String matterName,String knowledgeId,String hitType,String userId) {
        if(kbId==null||matterCode==null||knowledgeId==null) return 0;
        var h=new KbMatterKnowledgeHit();
        h.setKbId(kbId); h.setMatterCode(matterCode); h.setMatterName(matterName);
        h.setKnowledgeId(knowledgeId); h.setHitType(hitType==null||hitType.isBlank()?"view":hitType);
        h.setUserId(userId); h.setCreatedAt(LocalDateTime.now());
        hitMapper.insert(h);
        return 1;
    }
    /** 知识点可用性：已删除/已下架/已失效/已归档的知识点不进热点推荐 */
    private boolean isUsable(String knowledgeId) {
        var k=knowledgeMapper.selectById(knowledgeId);
        if(k==null||k.getDeletedAt()!=null) return false;
        String s=k.getStatus();
        return !"offline".equals(s)&&!"expired".equals(s)&&!"archived".equals(s);
    }
    private Map<String,Object> buildItem(String kbId,String knowledgeId,String title,int hitCount,boolean fromRel) {
        Map<String,Object> item=new LinkedHashMap<>();
        item.put("knowledgeId",knowledgeId);
        item.put("title",title==null?"":title);
        item.put("hitCount",hitCount);
        item.put("fromRel",fromRel);
        // 知识库对应知识点直链，供座席一键打开
        item.put("deepLink","/knowledge/"+kbId+"/knowledge-manage?knowledgeId="+knowledgeId);
        return item;
    }
    /** 系统配置读取：sys_config 缺失/非法时回退缺省值 */
    private int configInt(String key,int def) {
        try {
            var c=configService.getConfig(key);
            if(c!=null&&c.getConfigValue()!=null&&!c.getConfigValue().isBlank()) {
                return Integer.parseInt(c.getConfigValue().trim().replace("\"",""));
            }
        } catch (Exception e) { log.warn("读取配置 {} 失败，使用缺省 {}",key,def); }
        return def;
    }
    // 关联知识条目时冗余标题，便于列表展示与查看
    private void fillKnowledgeTitle(KbMatterKnowledgeRel rel) {
        if(rel.getKnowledgeId()!=null&&!rel.getKnowledgeId().isEmpty()) {
            var k=knowledgeMapper.selectById(rel.getKnowledgeId());
            if(k!=null) rel.setKnowledgeTitle(k.getTitle());
        }
    }
}
