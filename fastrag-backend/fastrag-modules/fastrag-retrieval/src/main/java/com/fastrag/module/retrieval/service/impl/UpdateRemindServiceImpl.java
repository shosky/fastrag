package com.fastrag.module.retrieval.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.publish.entity.KbUpdateLog;
import com.fastrag.module.publish.mapper.KbUpdateLogMapper;
import com.fastrag.module.retrieval.entity.KbUpdateRemind; import com.fastrag.module.retrieval.mapper.KbUpdateRemindMapper;
import com.fastrag.module.retrieval.service.UpdateRemindService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class UpdateRemindServiceImpl implements UpdateRemindService {
    private final KbUpdateRemindMapper mapper;
    private final KbUpdateLogMapper kbUpdateLogMapper;
    @Override public List<KbUpdateRemind> list(String kbId) {
        var w=new LambdaQueryWrapper<KbUpdateRemind>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbUpdateRemind::getKbId,kbId);
        return mapper.selectList(w.orderByDesc(KbUpdateRemind::getCreatedAt));
    }
    @Override public KbUpdateRemind get(String kbId) {
        return mapper.selectOne(new LambdaQueryWrapper<KbUpdateRemind>().eq(KbUpdateRemind::getKbId,kbId).last("LIMIT 1"));
    }
    @Override public KbUpdateRemind save(KbUpdateRemind remind) {
        if(remind.getId()==null||remind.getId().isEmpty()){
            if(remind.getEnabled()==null) remind.setEnabled(1);
            if(remind.getCronExpr()==null) remind.setCronExpr("0 9 * * *");
            mapper.insert(remind);
        } else { mapper.updateById(remind); }
        return remind;
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public Map<String,Object> remind(String kbId) {
        var remind=get(kbId);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("kbId",kbId);
        result.put("enabled",remind!=null&&remind.getEnabled()==1);
        result.put("lastRemindAt",remind!=null?remind.getLastRemindAt():null);
        result.put("cronExpr",remind!=null?remind.getCronExpr():"0 9 * * *");
        result.put("message","知识库有更新，请及时查看最新内容。");
        // 从 kb_update_log 查询实际更新次数
        long updateCount;
        if (remind != null && remind.getLastRemindAt() != null) {
            updateCount = kbUpdateLogMapper.selectCount(
                new LambdaQueryWrapper<KbUpdateLog>()
                    .eq(KbUpdateLog::getKbId, kbId)
                    .gt(KbUpdateLog::getTimestamp, remind.getLastRemindAt())
            );
        } else {
            updateCount = kbUpdateLogMapper.selectCount(
                new LambdaQueryWrapper<KbUpdateLog>()
                    .eq(KbUpdateLog::getKbId, kbId)
            );
        }
        result.put("updateCount", updateCount);
        if(remind!=null){ remind.setLastRemindAt(LocalDateTime.now()); mapper.updateById(remind); }
        return result;
    }
}
