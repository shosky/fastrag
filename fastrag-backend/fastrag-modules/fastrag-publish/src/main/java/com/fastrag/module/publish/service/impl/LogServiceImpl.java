package com.fastrag.module.publish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.entity.KbUpdateLog;
import com.fastrag.module.publish.mapper.KbLogMapper;
import com.fastrag.module.publish.mapper.KbUpdateLogMapper;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final KbLogMapper logMapper;
    private final KbUpdateLogMapper updateMapper;

    @Override
    public List<KbLog> listLogs(String kbId, String category) {
        var w = new LambdaQueryWrapper<KbLog>().eq(KbLog::getKbId, kbId);
        if (category != null) w.eq(KbLog::getCategory, category);
        return logMapper.selectList(w.orderByDesc(KbLog::getTimestamp));
    }

    @Override
    public List<KbUpdateLog> listUpdateLogs(String kbId, String type) {
        var w = new LambdaQueryWrapper<KbUpdateLog>().eq(KbUpdateLog::getKbId, kbId);
        if (type != null) w.eq(KbUpdateLog::getUpdateType, type);
        return updateMapper.selectList(w.orderByDesc(KbUpdateLog::getTimestamp));
    }

    @Override
    public void addLog(String kbId, String category, String action, String target, String detail, String operator) {
        try {
            KbLog log = new KbLog();
            log.setKbId(kbId);
            log.setCategory(category);
            log.setAction(action);
            log.setTarget(target);
            log.setDetail(detail);
            log.setOperator(operator);
            log.setTimestamp(LocalDateTime.now());
            logMapper.insert(log);
        } catch (Exception e) {
            log.error("Failed to add log", e);
        }
    }

    @Override
    public void addUpdateLog(String kbId, String updateType, String target, String detail, String operator) {
        try {
            KbUpdateLog updateLog = new KbUpdateLog();
            updateLog.setKbId(kbId);
            updateLog.setUpdateType(updateType);
            updateLog.setTarget(target);
            updateLog.setDetail(detail);
            updateLog.setOperator(operator);
            updateLog.setTimestamp(LocalDateTime.now());
            updateMapper.insert(updateLog);
        } catch (Exception e) {
            log.error("Failed to add update log", e);
        }
    }
}
