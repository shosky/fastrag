package com.fastrag.module.publish.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.publish.entity.*; import com.fastrag.module.publish.mapper.*;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class LogServiceImpl implements LogService {
    private final KbLogMapper logMapper; private final KbUpdateLogMapper updateMapper;
    @Override public List<KbLog> listLogs(String kbId,String category) { var w=new LambdaQueryWrapper<KbLog>().eq(KbLog::getKbId,kbId); if(category!=null)w.eq(KbLog::getCategory,category); return logMapper.selectList(w.orderByDesc(KbLog::getTimestamp)); }
    @Override public List<KbUpdateLog> listUpdateLogs(String kbId,String type) { var w=new LambdaQueryWrapper<KbUpdateLog>().eq(KbUpdateLog::getKbId,kbId); if(type!=null)w.eq(KbUpdateLog::getUpdateType,type); return updateMapper.selectList(w.orderByDesc(KbUpdateLog::getTimestamp)); }
}
