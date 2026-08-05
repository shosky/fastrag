package com.fastrag.module.operation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.operation.model.ModelMonitorData;
import com.fastrag.module.operation.service.ModelMonitorService;
import com.fastrag.module.platform.entity.ModelCallLog;
import com.fastrag.module.platform.mapper.ModelCallLogMapper;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 模型监控分析服务实现
 *
 * 数据来源：model_call_log 表（通过 fastrag-platform 模块的 Mapper 直接查询）
 */
@Service
@RequiredArgsConstructor
public class ModelMonitorServiceImpl implements ModelMonitorService {

    private final ModelCallLogMapper mapper;

    private static final DecimalFormat INT_FMT = new DecimalFormat("#,###");
    private static final DecimalFormat PCT_FMT = new DecimalFormat("#0.0");

    /** 组织过滤 SQL 片段：API Token 不过滤；无组织用户看不到任何数据 */
    private String orgCond() {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user != null && !user.getUserId().startsWith("api-token:")) {
            String orgId = user.getOrgId();
            if (orgId != null && !orgId.isEmpty()) {
                return " AND org_id =  + orgId + ";
            }
            return " AND 1=0";
        }
        return "";
    }

    @Override
    public ModelMonitorData getOverview(int timeRange, String keyword, int page, int pageSize) {
        // ===== 统一起始时间条件 =====
        String timeCond = "timestamp >= DATE_SUB(NOW(), INTERVAL " + timeRange + " DAY)";
        String prevTimeCond = "timestamp >= DATE_SUB(NOW(), INTERVAL " + (timeRange * 2) + " DAY) AND timestamp < DATE_SUB(NOW(), INTERVAL " + timeRange + " DAY)";

        // ===== 1. 指标卡片 =====
        long currentCalls = mapper.selectCount(new QueryWrapper<ModelCallLog>().apply(timeCond + orgCond()));
        long prevCalls = mapper.selectCount(new QueryWrapper<ModelCallLog>().apply(prevTimeCond + orgCond()));

        // 总 Token
        Number currentTokensNum = (Number) mapper.selectObjs(
                new QueryWrapper<ModelCallLog>().select("COALESCE(SUM(tokens), 0)").apply(timeCond + orgCond())
        ).stream().findFirst().orElse(0);
        long currentTokens = currentTokensNum.longValue();

        Number prevTokensNum = (Number) mapper.selectObjs(
                new QueryWrapper<ModelCallLog>().select("COALESCE(SUM(tokens), 0)").apply(prevTimeCond + orgCond())
        ).stream().findFirst().orElse(0);
        long prevTokens = prevTokensNum.longValue();

        // 平均响应时间
        Number avgDurationNum = (Number) mapper.selectObjs(
                new QueryWrapper<ModelCallLog>().select("COALESCE(AVG(duration), 0)").apply(timeCond + orgCond())
        ).stream().findFirst().orElse(0);
        long avgDuration = Math.round(avgDurationNum.doubleValue());

        Number prevAvgDurationNum = (Number) mapper.selectObjs(
                new QueryWrapper<ModelCallLog>().select("COALESCE(AVG(duration), 0)").apply(prevTimeCond + orgCond())
        ).stream().findFirst().orElse(0);
        long prevAvgDuration = Math.round(prevAvgDurationNum.doubleValue());

        // 总失败次数
        long currentFails = mapper.selectCount(new QueryWrapper<ModelCallLog>().eq("status", "failed").apply(timeCond + orgCond()));
        long prevFails = mapper.selectCount(new QueryWrapper<ModelCallLog>().eq("status", "failed").apply(prevTimeCond + orgCond()));

        String tokenChange = calcChange(prevTokens, currentTokens, false);
        String callsChange = calcChange(prevCalls, currentCalls, false);
        String durationChange = calcChange(prevAvgDuration, avgDuration, true);
        String failsChange = calcChange(prevFails, currentFails, true);

        List<ModelMonitorData.MetricItem> metrics = List.of(
                ModelMonitorData.MetricItem.builder().label("总 Token 消耗").value(INT_FMT.format(currentTokens)).change(tokenChange).trend(tokenChange.startsWith("+") ? "up" : "down").build(),
                ModelMonitorData.MetricItem.builder().label("API 调用次数").value(INT_FMT.format(currentCalls)).change(callsChange).trend(callsChange.startsWith("+") ? "up" : "down").build(),
                ModelMonitorData.MetricItem.builder().label("平均响应时间").value(avgDuration + "ms").change(durationChange).trend(durationChange.startsWith("-") ? "down" : "up").build(),
                ModelMonitorData.MetricItem.builder().label("总失败次数").value(INT_FMT.format(currentFails)).change(failsChange).trend(failsChange.startsWith("-") ? "down" : "up").build()
        );

        // ===== 2. 模型使用分布 =====
        List<Map<String, Object>> distRows = mapper.selectMaps(
                new QueryWrapper<ModelCallLog>()
                        .select("model_id, COUNT(*) as calls, SUM(tokens) as tokens")
                        .apply(timeCond + orgCond())
                        .groupBy("model_id")
                        .orderByDesc("tokens")
        );
        long totalDistTokens = distRows.stream()
                .mapToLong(r -> ((Number) r.get("tokens")).longValue())
                .sum();
        List<ModelMonitorData.ModelDistItem> distribution = distRows.stream().map(r -> {
            String modelId = (String) r.get("model_id");
            long t = ((Number) r.get("tokens")).longValue();
            double pct = totalDistTokens == 0 ? 0 : Math.round(t * 1000.0 / totalDistTokens) / 10.0;
            return ModelMonitorData.ModelDistItem.builder()
                    .name(modelId != null ? modelId : "unknown")
                    .percentage(pct)
                    .token(INT_FMT.format(t))
                    .build();
        }).collect(Collectors.toList());

        // ===== 3. 高消耗应用排行 =====
        List<Map<String, Object>> appRows = mapper.selectMaps(
                new QueryWrapper<ModelCallLog>()
                        .select("caller, SUM(tokens) as tokens, COUNT(*) as calls")
                        .apply(timeCond + orgCond())
                        .groupBy("caller")
                        .orderByDesc("tokens")
                        .last("LIMIT 5")
        );
        List<ModelMonitorData.TopAppItem> topApps = new ArrayList<>();
        int rank = 0;
        for (var row : appRows) {
            rank++;
            String caller = (String) row.get("caller");
            long t = ((Number) row.get("tokens")).longValue();
            // 按 ¥5/百万 token 估算成本
            String cost = "—";
            if (t > 0) {
                double costVal = t * 5.0 / 1_000_000;
                cost = "¥" + String.format("%.2f", costVal);
            }
            topApps.add(ModelMonitorData.TopAppItem.builder()
                    .rank(rank)
                    .name(caller != null ? caller : "unknown")
                    .token(INT_FMT.format(t))
                    .cost(cost)
                    .build());
        }

        // ===== 4. 模型调用统计（分页） =====
        // 先按 model_id 分组统计
        boolean hasKeyword = keyword != null && !keyword.isEmpty();
        List<Map<String, Object>> allModelStats = mapper.selectMaps(
                new QueryWrapper<ModelCallLog>()
                        .select("model_id, COUNT(*) as calls, SUM(tokens) as tokens, SUM(CASE WHEN status='failed' THEN 1 ELSE 0 END) as fails")
                        .apply(timeCond + orgCond())
                        .groupBy("model_id")
                        .orderByDesc("tokens")
        );

        // 筛选
        List<Map<String, Object>> filtered = allModelStats;
        if (hasKeyword) {
            String kw = keyword.toLowerCase();
            filtered = allModelStats.stream()
                    .filter(r -> {
                        String mid = (String) r.get("model_id");
                        return mid != null && mid.toLowerCase().contains(kw);
                    })
                    .collect(Collectors.toList());
        }

        long total = filtered.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        List<Map<String, Object>> pageRows = filtered.subList(
                Math.min(fromIndex, filtered.size()),
                Math.min(toIndex, filtered.size())
        );

        List<ModelMonitorData.ModelStatsItem> statsItems = pageRows.stream()
                .map(r -> {
                    String modelId = (String) r.get("model_id");
                    long calls = ((Number) r.get("calls")).longValue();
                    long fails = ((Number) r.get("fails")).longValue();
                    long t = ((Number) r.get("tokens")).longValue();
                    String cost = t > 0 ? "¥" + String.format("%.2f", t * 5.0 / 1_000_000) : "—";
                    return ModelMonitorData.ModelStatsItem.builder()
                            .code(modelId != null ? modelId : "unknown")
                            .calls(calls)
                            .fails(fails)
                            .token(INT_FMT.format(t))
                            .cost(cost)
                            .build();
                })
                .collect(Collectors.toList());

        PageResult<ModelMonitorData.ModelStatsItem> statsPage =
                PageResult.of(statsItems, total, page, pageSize);

        // ===== 组装返回 =====
        return ModelMonitorData.builder()
                .metrics(metrics)
                .distribution(distribution)
                .topApps(topApps)
                .stats(statsPage)
                .build();
    }

    /**
     * 计算变化百分比
     *
     * @param prev      前一周期值
     * @param current   当前周期值
     * @param smallerIsBetter 是否越小越好（响应时间、失败数 → down 为好）
     * @return 格式化变化字符串，如 "+12.3%"
     */
    private String calcChange(long prev, long current, boolean smallerIsBetter) {
        if (prev == 0) {
            return current > 0 ? "新增" : "0%";
        }
        double pct = (current - prev) * 100.0 / prev;
        String sign = pct >= 0 ? "+" : "";
        return sign + PCT_FMT.format(pct) + "%";
    }
}
