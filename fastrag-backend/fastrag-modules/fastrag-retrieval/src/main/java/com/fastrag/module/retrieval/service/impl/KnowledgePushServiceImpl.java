package com.fastrag.module.retrieval.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.platform.entity.SysNotification; import com.fastrag.module.platform.mapper.SysNotificationMapper;
import com.fastrag.module.retrieval.entity.KbKnowledgePush; import com.fastrag.module.retrieval.mapper.KbKnowledgePushMapper;
import com.fastrag.module.retrieval.service.KnowledgePushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgePushServiceImpl implements KnowledgePushService {
    private final KbKnowledgePushMapper mapper;
    private final SysNotificationMapper notificationMapper;
    private final ObjectMapper objectMapper;
    @Override public List<KbKnowledgePush> list(String kbId, String status) {
        var w=new LambdaQueryWrapper<KbKnowledgePush>().eq(KbKnowledgePush::getKbId,kbId);
        if(status!=null&&!status.isEmpty()) w.eq(KbKnowledgePush::getStatus,status);
        return mapper.selectList(w.orderByDesc(KbKnowledgePush::getCreatedAt));
    }
    @Override public KbKnowledgePush get(String id) { return mapper.selectById(id); }
    @Override public KbKnowledgePush create(String kbId,KbKnowledgePush push) {
        push.setId(null); push.setKbId(kbId);
        if(push.getStatus()==null) push.setStatus("draft");
        if(push.getPushType()==null) push.setPushType("manual");
        mapper.insert(push); return push;
    }
    @Override public KbKnowledgePush update(String id,KbKnowledgePush push) {
        var e=mapper.selectById(id);
        if(e==null) throw new RuntimeException("知识推送不存在");
        push.setId(id); mapper.updateById(push); return mapper.selectById(id);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    // 发送推送：按 target_users(JSON 数组) 逐个写系统通知；未指定目标则写全员通知
    @Override public Map<String,Object> send(String id, String operator) {
        var p=mapper.selectById(id);
        if(p==null) throw new RuntimeException("知识推送不存在");
        List<String> targets=new ArrayList<>();
        try { if(p.getTargetUsers()!=null&&!p.getTargetUsers().isBlank()) {
            var l=objectMapper.readValue(p.getTargetUsers(), List.class);
            l.forEach(u->targets.add(String.valueOf(u)));
        } } catch (Exception ignore) { }
        int sent=0;
        try {
            if(targets.isEmpty()) {
                notificationMapper.insert(buildNotification(p, null));
                sent++;
            } else {
                for(var u:targets) { notificationMapper.insert(buildNotification(p, u)); sent++; }
            }
        } catch (Exception ignore) { }
        p.setStatus("sent"); p.setPushedAt(LocalDateTime.now()); mapper.updateById(p);
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("id",id); r.put("status","sent"); r.put("sent",sent); r.put("pushedAt",p.getPushedAt());
        return r;
    }
    private SysNotification buildNotification(KbKnowledgePush p, String targetUser) {
        var n=new SysNotification();
        n.setTitle("知识推送："+(p.getTitle()==null?"":p.getTitle()));
        n.setContent(p.getContent()==null?"":p.getContent());
        n.setNotifyType("knowledge_push"); n.setSourceType("kb"); n.setSourceId(p.getKbId());
        n.setTargetUser(targetUser); n.setStatus("unread"); n.setCreatedAt(LocalDateTime.now());
        return n;
    }
}
