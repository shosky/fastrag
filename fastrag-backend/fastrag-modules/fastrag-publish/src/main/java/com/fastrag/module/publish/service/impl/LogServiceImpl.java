package com.fastrag.module.publish.service.impl;

/**
 * 知识库操作日志与更新日志服务实现。
 *
 * <p>实现 {@link LogService} 接口，封装知识库操作日志（{@link KbLog}）和
 * 更新日志（{@link KbUpdateLog}）的查询与写入逻辑。</p>
 *
 * <h3>核心实现逻辑：</h3>
 * <ul>
 *   <li>查询：通过 MyBatis-Plus 的 LambdaQueryWrapper 按 kbId 和可选的 category/type 过滤，
 *       结果按时间倒序排列</li>
 *   <li>写入：构建实体对象后调用 Mapper 的 insert 方法，所有写入操作捕获异常并记录日志，
 *       确保日志写入失败不影响主业务流程</li>
 *   <li>兼容性：字符串参数的写入方法内部将字符串解析为枚举后委托给枚举参数版本执行</li>
 *   <li>已读标记：markUpdateLogRead 为空实现，已读状态由前端本地处理</li>
 * </ul>
 *
 * @see LogService
 * @see KbLogMapper
 * @see KbUpdateLogMapper
 */
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.entity.KbUpdateLog;
import com.fastrag.module.publish.mapper.KbLogMapper;
import com.fastrag.module.publish.mapper.KbUpdateLogMapper;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final KbLogMapper logMapper;
    private final KbUpdateLogMapper updateMapper;

    // ==================== 查询 ====================

    @Override
    public PageResult<KbLog> listLogs(String kbId, String category, String keyword, int page, int pageSize) {
        var w = new LambdaQueryWrapper<KbLog>()
                .eq(KbLog::getKbId, kbId)
                .eq(category != null, KbLog::getCategory, category);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(KbLog::getTarget, keyword)
                    .or().like(KbLog::getDetail, keyword)
                    .or().like(KbLog::getOperator, keyword));
        }
        var pg = logMapper.selectPage(new Page<>(page, pageSize), w.orderByDesc(KbLog::getTimestamp));
        return PageResult.of(pg.getRecords(), pg.getTotal(), page, pageSize);
    }

    @Override
    public Map<String, Long> countByCategory(String kbId) {
        long total = logMapper.selectCount(new LambdaQueryWrapper<KbLog>().eq(KbLog::getKbId, kbId));
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", total);
        for (LogCategory c : LogCategory.values()) {
            long cnt = logMapper.selectCount(new LambdaQueryWrapper<KbLog>()
                    .eq(KbLog::getKbId, kbId).eq(KbLog::getCategory, c.name()));
            stats.put(c.name(), cnt);
        }
        return stats;
    }

    @Override
    public List<KbUpdateLog> listUpdateLogs(String kbId, String type) {
        var w = new LambdaQueryWrapper<KbUpdateLog>().eq(KbUpdateLog::getKbId, kbId);
        if (type != null) w.eq(KbUpdateLog::getUpdateType, type);
        return updateMapper.selectList(w.orderByDesc(KbUpdateLog::getTimestamp));
    }

    // ==================== 写入（字符串参数，向后兼容） ====================

    @Override
    public void addLog(String kbId, String category, String action, String target, String detail, String operator) {
        addLog(kbId, parseLogCategory(category), parseActionType(action), target, detail, operator, "success", null);
    }

    @Override
    public void addUpdateLog(String kbId, String updateType, String target, String detail, String operator) {
        addUpdateLog(kbId, updateType, target, detail, operator, null, null);
    }

    // ==================== 写入（枚举参数 + status + extra） ====================

    @Override
    public void addLog(String kbId, LogCategory category, ActionType action, String target, String detail, String operator, String status) {
        addLog(kbId, category, action, target, detail, operator, status, null);
    }

    @Override
    public void addLog(String kbId, LogCategory category, ActionType action, String target, String detail, String operator, String status, String extra) {
        try {
            KbLog log = new KbLog();
            log.setKbId(kbId);
            log.setCategory(category != null ? category.name() : null);
            log.setAction(action != null ? action.name() : null);
            log.setTarget(target);
            log.setDetail(detail);
            log.setOperator(operator);
            log.setStatus(status != null ? status : "success");
            log.setExtra(extra);
            log.setTimestamp(LocalDateTime.now());
            logMapper.insert(log);
        } catch (Exception e) {
            log.error("Failed to add log", e);
        }
    }

    @Override
    public void addUpdateLog(String kbId, String updateType, String target, String detail, String operator, String oldValue, String newValue) {
        try {
            KbUpdateLog updateLog = new KbUpdateLog();
            updateLog.setKbId(kbId);
            updateLog.setUpdateType(updateType);
            updateLog.setTarget(target);
            updateLog.setDetail(detail);
            updateLog.setOldValue(oldValue);
            updateLog.setNewValue(newValue);
            updateLog.setOperator(operator);
            updateLog.setTimestamp(LocalDateTime.now());
            updateMapper.insert(updateLog);
        } catch (Exception e) {
            log.error("Failed to add update log", e);
        }
    }

    // ==================== 辅助方法 ====================

    @Override
    public void markUpdateLogRead(String id) {
        // 标记已读功能由前端本地处理，后端不做持久化
    }

    /**
     * 将字符串解析为 LogCategory 枚举，未知值返回 null
     */
    private LogCategory parseLogCategory(String category) {
        if (category == null || category.isBlank()) return null;
        try {
            return LogCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 将字符串解析为 ActionType 枚举，未知值返回 null
     */
    private ActionType parseActionType(String action) {
        if (action == null || action.isBlank()) return null;
        try {
            return ActionType.valueOf(action);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
