package com.fastrag.module.operation.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.operation.entity.UserFeedback; import com.fastrag.module.operation.mapper.UserFeedbackMapper;
import com.fastrag.module.operation.entity.ChatSession;
import com.fastrag.module.operation.mapper.ChatSessionMapper;
import com.fastrag.module.operation.service.FeedbackService;
import com.fastrag.module.application.entity.App;
import com.fastrag.module.application.mapper.AppMapper;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {
    private final UserFeedbackMapper mapper;
    private final ChatSessionMapper chatSessionMapper;
    private final AppMapper appMapper;
    private final KbAccessChecker accessChecker;

    /** 组织过滤（反馈归属提交者组织）：API Token 不过滤 */
    private void applyOrgScope(LambdaQueryWrapper<UserFeedback> w) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user != null && !user.getUserId().startsWith("api-token:")) {
            w.eq(UserFeedback::getOrgId, user.getOrgId());
        }
    }

    private void applyOrgScope(QueryWrapper<UserFeedback> w) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user != null && !user.getUserId().startsWith("api-token:")) {
            w.eq("org_id", user.getOrgId());
        }
    }

    /** 校验反馈属于当前用户组织（管理操作） */
    private void requireOwnOrg(UserFeedback fb) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user == null || fb == null) throw BusinessException.notFound("反馈不存在");
        if (!user.getUserId().startsWith("api-token:")
                && !Objects.equals(user.getOrgId(), fb.getOrgId())) {
            throw BusinessException.forbidden("无权操作该反馈");
        }
    }

    @Override public PageResult<UserFeedback> page(String kbId,String feedback,String status,int page,int pageSize) {
        var w=new LambdaQueryWrapper<UserFeedback>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(UserFeedback::getKbId,kbId);
        if(feedback!=null&&!feedback.isEmpty()) w.eq(UserFeedback::getFeedback,feedback);
        if(status!=null&&!status.isEmpty()) w.eq(UserFeedback::getStatus,status);
        applyOrgScope(w);
        w.orderByDesc(UserFeedback::getCreatedAt);
        var pg=mapper.selectPage(new Page<>(page,pageSize),w);
        return PageResult.of(pg.getRecords(),pg.getTotal(),page,pageSize);
    }
    @Override public List<UserFeedback> list(String kbId) {
        var w=new LambdaQueryWrapper<UserFeedback>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(UserFeedback::getKbId,kbId);
        applyOrgScope(w);
        return mapper.selectList(w.orderByDesc(UserFeedback::getCreatedAt).last("LIMIT 200"));
    }
    @Override public void create(UserFeedback fb) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user != null) fb.setOrgId(user.getOrgId()); // 反馈归属提交者组织（防伪造）
        if(fb.getStatus()==null) fb.setStatus("pending");
        if(fb.getFeedback()==null) fb.setFeedback("like");
        mapper.insert(fb);
    }
    @Override public UserFeedback update(Long id,UserFeedback fb) {
        requireOwnOrg(mapper.selectById(id));
        fb.setId(id); mapper.updateById(fb); return mapper.selectById(id);
    }
    @Override public void delete(Long id) {
        requireOwnOrg(mapper.selectById(id));
        mapper.deleteById(id);
    }
    @Override public UserFeedback reply(Long id,String reply,String operator) {
        var fb=mapper.selectById(id);
        requireOwnOrg(fb);
        if(fb==null) return null;
        fb.setReply(reply); fb.setProcessedBy(operator); fb.setProcessedAt(LocalDateTime.now()); fb.setStatus("resolved");
        mapper.updateById(fb); return fb;
    }
    @Override public Map<String,Object> statistics(String kbId) {
        var w=new LambdaQueryWrapper<UserFeedback>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(UserFeedback::getKbId,kbId);
        applyOrgScope(w);
        List<UserFeedback> all=mapper.selectList(w);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("total",all.size());
        Map<String,Long> byType=new LinkedHashMap<>();
        Map<String,Long> byStatus=new LinkedHashMap<>();
        long likeCount=0,scoreSum=0,scoreCnt=0;
        for(var fb:all){
            byType.merge(fb.getFeedback()!=null?fb.getFeedback():"unknown",1L,Long::sum);
            byStatus.merge(fb.getStatus()!=null?fb.getStatus():"pending",1L,Long::sum);
            if("like".equals(fb.getFeedback())) likeCount++;
            if(fb.getScore()!=null&&fb.getScore()>0){ scoreSum+=fb.getScore(); scoreCnt++; }
        }
        result.put("byType",byType);
        result.put("byStatus",byStatus);
        result.put("satisfactionRate",all.isEmpty()?0.0:Math.round(likeCount*10000.0/all.size())/100.0);
        result.put("avgScore",scoreCnt==0?0.0:Math.round(scoreSum*100.0/scoreCnt)/100.0);
        result.put("resolvedRate",all.isEmpty()?0.0:Math.round(byStatus.getOrDefault("resolved",0L)*10000.0/all.size())/100.0);
        return result;
    }

    @Override public Map<String,Object> getOverview(String kbId) {
        Map<String,Object> result=new LinkedHashMap<>();

        // ===== 1. 指标卡片（全部走 SQL 聚合，避免全量加载） =====
        // 累计问答量 → chat_session COUNT（按当前用户可访问的知识库过滤）
        var csWrapper=new LambdaQueryWrapper<ChatSession>();
        if(kbId!=null&&!kbId.isEmpty()) csWrapper.eq(ChatSession::getKbId,kbId);
        LoginUser cur = SecurityUtil.getCurrentUser();
        if (cur != null && !cur.getUserId().startsWith("api-token:")) {
            List<String> accessible = accessChecker.getAccessibleKbIds(cur.getUserId(), cur.getOrgId());
            if (accessible.isEmpty()) accessible = List.of("__none__");
            csWrapper.in(ChatSession::getKbId, accessible);
        }
        long totalQaCount=chatSessionMapper.selectCount(csWrapper);

        // 反馈指标 → SQL 聚合查询
        var fbCond=new QueryWrapper<UserFeedback>();
        if(kbId!=null&&!kbId.isEmpty()) fbCond.eq("kb_id",kbId);
        applyOrgScope(fbCond);
        Map<String,Object> fbAgg=mapper.selectMaps(
            fbCond.select(
                "COUNT(*) as total",
                "SUM(CASE WHEN feedback='like' THEN 1 ELSE 0 END) as likes",
                "SUM(CASE WHEN status NOT IN ('resolved','ignored') THEN 1 ELSE 0 END) as pending"
            )
        ).stream().findFirst().orElse(new HashMap<>());
        long totalFb=((Number)fbAgg.getOrDefault("total",0)).longValue();
        long likeCount=((Number)fbAgg.getOrDefault("likes",0)).longValue();
        long pendingCount=((Number)fbAgg.getOrDefault("pending",0)).longValue();

        double satisfactionRate=totalFb==0?0:Math.round(likeCount*10000.0/totalFb)/100.0;
        double feedbackRate=totalQaCount==0?0:Math.round(totalFb*10000.0/totalQaCount)/100.0;
        double unresolvedRate=totalFb==0?0:Math.round(pendingCount*10000.0/totalFb)/100.0;

        Map<String,Object> metrics=new LinkedHashMap<>();
        metrics.put("totalQaCount",totalQaCount);
        metrics.put("totalFeedbackCount",totalFb);
        metrics.put("satisfactionRate",satisfactionRate);
        metrics.put("feedbackRate",feedbackRate);
        metrics.put("unresolvedRate",unresolvedRate);
        result.put("metrics",metrics);

        // ===== 2. 问题分类分析 =====
        // 从 feedback.category 字段 GROUP BY
        var catQ = new QueryWrapper<UserFeedback>()
                .select("category, COUNT(*) as cnt")
                .isNotNull("category")
                .ne("category","");
        applyOrgScope(catQ);
        List<Map<String,Object>> catRows=mapper.selectMaps(
            catQ.groupBy("category").orderByDesc("cnt")
        );
        long catTotal=catRows.stream().mapToLong(r->((Number)r.get("cnt")).longValue()).sum();
        List<Map<String,Object>> categories=new ArrayList<>();
        for(var row:catRows){
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("name",row.get("category"));
            long cnt=((Number)row.get("cnt")).longValue();
            item.put("count",cnt);
            item.put("percentage",catTotal==0?0:(int)Math.round(cnt*100.0/catTotal));
            categories.add(item);
        }
        result.put("questionCategories",categories);

        // ===== 3. 高频词云 =====
        // 仅加载 query 列（最多 2000 条），避免全量加载
        var queryCond=new QueryWrapper<UserFeedback>();
        if(kbId!=null&&!kbId.isEmpty()) queryCond.eq("kb_id",kbId);
        applyOrgScope(queryCond);
        queryCond.select("query").isNotNull("query").ne("query","").last("LIMIT 2000");
        List<Map<String,Object>> queryRows=mapper.selectMaps(queryCond);
        Map<String,Integer> wordFreq=new LinkedHashMap<>();
        for(var row:queryRows){
            String q=(String)row.get("query");
            if(q==null||q.isEmpty()) continue;
            String[] parts=q.toLowerCase().split("[^\\u4e00-\\u9fa5a-zA-Z0-9]+");
            for(String p:parts){
                if(p.length()<2) continue;
                wordFreq.merge(p,1,Integer::sum);
            }
        }
        List<Map<String,Object>> hotKeywords=wordFreq.entrySet().stream()
            .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
            .limit(20)
            .map(e->{
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("word",e.getKey());
                item.put("count",e.getValue());
                return item;
            }).collect(Collectors.toList());
        result.put("hotKeywords",hotKeywords);

        // ===== 4. 应用满意度排行 =====
        // GROUP BY app_id，JOIN app 表取名称
        var appQ = new QueryWrapper<UserFeedback>()
                .select("app_id, COUNT(*) as cnt, SUM(CASE WHEN feedback='like' THEN 1 ELSE 0 END) as likes, AVG(score) as avg_score, SUM(CASE WHEN score>0 THEN 1 ELSE 0 END) as score_cnt")
                .isNotNull("app_id")
                .ne("app_id","");
        applyOrgScope(appQ);
        List<Map<String,Object>> appRows=mapper.selectMaps(
            appQ.groupBy("app_id").orderByDesc("cnt")
        );
        List<Map<String,Object>> appRanking=new ArrayList<>();
        int rank=0;
        for(var row:appRows){
            rank++;
            String appId=(String)row.get("app_id");
            long cnt=((Number)row.get("cnt")).longValue();
            long likes=((Number)row.get("likes")).longValue();
            double sat=cnt==0?0:Math.round(likes*10000.0/cnt)/100.0;

            // 查询应用名称
            String appName=appId;
            try{
                App app=appMapper.selectById(appId);
                if(app!=null&&app.getName()!=null) appName=app.getName();
            }catch(Exception ignored){}

            Map<String,Object> item=new LinkedHashMap<>();
            item.put("rank",rank);
            item.put("appId",appId);
            item.put("name",appName);
            item.put("satisfaction",sat);
            item.put("feedbackCount",cnt);
            appRanking.add(item);
        }
        result.put("appSatisfactionRanking",appRanking);

        return result;
    }
}
