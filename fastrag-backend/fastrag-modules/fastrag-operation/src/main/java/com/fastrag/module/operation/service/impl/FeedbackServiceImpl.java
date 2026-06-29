package com.fastrag.module.operation.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.operation.entity.UserFeedback; import com.fastrag.module.operation.mapper.UserFeedbackMapper;
import com.fastrag.module.operation.service.FeedbackService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {
    private final UserFeedbackMapper mapper;
    @Override public PageResult<UserFeedback> page(String kbId,String feedback,String status,int page,int pageSize) {
        var w=new LambdaQueryWrapper<UserFeedback>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(UserFeedback::getKbId,kbId);
        if(feedback!=null&&!feedback.isEmpty()) w.eq(UserFeedback::getFeedback,feedback);
        if(status!=null&&!status.isEmpty()) w.eq(UserFeedback::getStatus,status);
        w.orderByDesc(UserFeedback::getCreatedAt);
        var pg=mapper.selectPage(new Page<>(page,pageSize),w);
        return PageResult.of(pg.getRecords(),pg.getTotal(),page,pageSize);
    }
    @Override public List<UserFeedback> list(String kbId) {
        var w=new LambdaQueryWrapper<UserFeedback>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(UserFeedback::getKbId,kbId);
        return mapper.selectList(w.orderByDesc(UserFeedback::getCreatedAt).last("LIMIT 200"));
    }
    @Override public void create(UserFeedback fb) {
        if(fb.getStatus()==null) fb.setStatus("pending");
        if(fb.getFeedback()==null) fb.setFeedback("like");
        mapper.insert(fb);
    }
    @Override public UserFeedback update(Long id,UserFeedback fb) {
        fb.setId(id); mapper.updateById(fb); return mapper.selectById(id);
    }
    @Override public void delete(Long id) { mapper.deleteById(id); }
    @Override public UserFeedback reply(Long id,String reply,String operator) {
        var fb=mapper.selectById(id);
        if(fb==null) return null;
        fb.setReply(reply); fb.setProcessedBy(operator); fb.setProcessedAt(LocalDateTime.now()); fb.setStatus("resolved");
        mapper.updateById(fb); return fb;
    }
    @Override public Map<String,Object> statistics(String kbId) {
        var w=new LambdaQueryWrapper<UserFeedback>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(UserFeedback::getKbId,kbId);
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
}
