package com.fastrag.module.retrieval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.retrieval.entity.KbRetrievalLog;
import com.fastrag.module.retrieval.mapper.KbRetrievalLogMapper;
import com.fastrag.module.retrieval.service.RetrievalLogService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 检索日志（按组织隔离）：
 * - 日志作为审计记录保留（删库不清理），但接口仅返回「本组织库 ∪ ACL 授权库」的日志
 * - 平台级 API Token（程序化访问）不过滤
 */
@Service
@RequiredArgsConstructor
public class RetrievalLogServiceImpl implements RetrievalLogService {

    private final KbRetrievalLogMapper mapper;
    private final KbAccessChecker accessChecker;

    /** 当前用户可访问的 kbId 集合；API Token 返回 null（不过滤） */
    private List<String> accessibleKbIds() {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user == null) return Collections.emptyList();
        if (user.getUserId().startsWith("api-token:")) return null;
        return accessChecker.getAccessibleKbIds(user.getUserId(), user.getOrgId());
    }

    @Override
    public void log(KbRetrievalLog log) {
        mapper.insert(log);
    }

    @Override
    public void update(KbRetrievalLog log) {
        mapper.updateById(log);
    }

    @Override
    public PageResult<KbRetrievalLog> page(String kbId, Boolean hasResult, int page, int pageSize) {
        var w = new LambdaQueryWrapper<KbRetrievalLog>();
        List<String> accessible = accessibleKbIds();
        if (kbId != null && !kbId.isEmpty()) {
            // 指定库：不在可访问范围内视为无数据（不泄露库是否存在）
            if (accessible != null && !accessible.contains(kbId)) {
                return PageResult.of(Collections.emptyList(), 0, page, pageSize);
            }
            w.eq(KbRetrievalLog::getKbId, kbId);
        } else if (accessible != null) {
            if (accessible.isEmpty()) {
                return PageResult.of(Collections.emptyList(), 0, page, pageSize);
            }
            w.in(KbRetrievalLog::getKbId, accessible);
        }
        if (hasResult != null) w.eq(KbRetrievalLog::getHasResult, hasResult);
        w.orderByDesc(KbRetrievalLog::getCreatedAt);
        var pg = mapper.selectPage(new Page<>(page, pageSize), w);
        return PageResult.of(pg.getRecords(), pg.getTotal(), page, pageSize);
    }

    @Override
    public Map<String, Object> analysis(String kbId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> accessible = accessibleKbIds();

        // 指定库：不在可访问范围内返回空统计
        if (kbId != null && !kbId.isEmpty()) {
            if (accessible != null && !accessible.contains(kbId)) {
                return emptyAnalysis();
            }
        } else if (accessible != null && accessible.isEmpty()) {
            return emptyAnalysis();
        }

        // 聚合统计：kbId 指定时按单个库；未指定时按可访问集合（API Token 传 null 全量）
        Map<String, Object> stats = mapper.aggregateStats(kbId, accessible);

        long totalQueries = toLong(stats.get("total_queries"));
        long noResultCount = toLong(stats.get("no_result_count"));
        double avgLatencyMs = toDouble(stats.get("avg_latency_ms"));
        double avgHitCount = toDouble(stats.get("avg_hit_count"));

        result.put("totalQueries", totalQueries);
        result.put("noResultCount", noResultCount);
        result.put("noResultRate", totalQueries > 0
                ? Math.round(noResultCount * 10000.0 / totalQueries) / 100.0
                : 0.0);
        result.put("avgLatencyMs", avgLatencyMs);
        result.put("avgHitCount", avgHitCount);

        // 热门查询 Top10
        List<Map<String, Object>> topQueries = mapper.selectTopQueries(kbId, accessible);
        result.put("topQueries", topQueries);

        // 无结果查询 Top10
        List<Map<String, Object>> noResultQueries = mapper.selectNoResultQueries(kbId, accessible);
        result.put("noResultQueries", noResultQueries);

        return result;
    }

    /** 无权限/无数据时的空统计结构 */
    private Map<String, Object> emptyAnalysis() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalQueries", 0L);
        result.put("noResultCount", 0L);
        result.put("noResultRate", 0.0);
        result.put("avgLatencyMs", 0.0);
        result.put("avgHitCount", 0.0);
        result.put("topQueries", Collections.emptyList());
        result.put("noResultQueries", Collections.emptyList());
        return result;
    }

    private long toLong(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof BigDecimal) return ((BigDecimal) value).doubleValue();
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }
}
