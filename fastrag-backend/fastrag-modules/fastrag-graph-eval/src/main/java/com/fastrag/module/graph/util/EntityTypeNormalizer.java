package com.fastrag.module.graph.util;

/**
 * 实体类型归一化工具类（对标 LightRAG 类型收敛策略）。
 *
 * <p>在知识图谱构建过程中，LLM 抽取的实体类型（label）往往不收敛，容易产生数百种冗余类型和大量 UNKNOWN 堆积，
 * 严重影响图谱质量和下游检索效果。本工具通过白名单匹配与编辑距离归并策略，将 LLM 输出的实体类型收敛到可控范围内。
 * 归一化优先级为：白名单精确匹配（忽略大小写）→ 编辑距离 ≤1 的模糊归并（如 "故障类别"→"故障类型"）→ 未命中则归为 UNKNOWN。</p>
 *
 * <p>白名单来源支持外部配置（application.yml 中 {@code graph.entity-type-whitelist}，逗号分隔），
 * 为空时使用内置的 {@link #DEFAULT_WHITELIST} 默认集合（覆盖故障、告警、指标、设备、系统、文档等十余个业务领域的高频实体类型）。
 * 归一化后的实体类型统一写入 {@link com.fastrag.module.graph.entity.KbGraphEntity} 的 label 字段，
 * 确保 {@link com.fastrag.module.graph.entity.KbGraphEntity} 表中实体类型的一致性和可查询性。</p>
 *
 * <p>本类为无状态工具类，仅包含静态方法，主要被 {@link com.fastrag.module.graph.util.ExtractionNormalizer}
 * 和图谱构建流程间接调用。</p>
 */
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 实体类型归一化器（对标 LightRAG 类型收敛）
 *
 * <p>LLM 抽取的实体 label 不收敛会导致实体类型爆炸（如数百种类型 + UNKNOWN 堆积）。
 * 归一策略：白名单精确命中保留 → 编辑距离 ≤1 归并 → 未命中归 UNKNOWN。
 * 白名单可覆盖（application.yml: graph.entity-type-whitelist，逗号分隔），为空时使用默认集合。</p>
 */
public final class EntityTypeNormalizer {

    /** 未命中白名单的兜底类型 */
    public static final String UNKNOWN = "UNKNOWN";

    /** 默认白名单（来自生产知识库高频实体类型归纳，可按需裁剪） */
    public static final Set<String> DEFAULT_WHITELIST = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "故障类型", "故障", "故障现象", "故障等级", "故障处置", "故障时长", "故障设备",
            "根因", "原因", "问题", "问题类型", "异常", "异常类型", "异常信息", "异常关键字",
            "错误类型", "错误信息", "错误代码",
            "告警", "告警类型", "告警状态", "告警方式", "预警", "预警阈值",
            "风险", "风险类型", "风险等级", "隐患",
            // 值型/时间型"类型"（数值、日期、时间、指标、阈值等）已于 2026-09-06 移除：
            // 它们会诱导 LLM 把纯数值、日期、单据号抽成实体（值内容应作为相关实体的 attributes）；
            // 白名单漏出后还有 GraphBuildConsumer.isValueLikeEntityName 代码侧兜底
            "巡检对象", "巡检类型", "巡检指标", "巡检流程", "巡检项",
            "监控", "监控项", "监控措施", "监控系统", "监控平台", "监控对象",
            "参数", "配置", "配置项", "系统参数", "配置参数", "变量",
            "设备", "设备部件", "硬件", "硬件资源", "组件", "组件类型", "系统组件",
            "系统", "系统状态", "操作系统", "数据库", "数据库组件", "中间件", "进程", "服务",
            "应用", "平台", "接口", "接口类型", "接口章节", "协议", "脚本", "脚本类型",
            "任务", "任务类型", "操作", "操作类型", "操作内容", "操作要求",
            "流程", "业务流程", "步骤", "方法", "技术", "技术方法", "技术架构", "方案",
            "解决方案", "处置方案", "处置措施", "整改措施", "整改方案", "整改内容",
            "优化措施", "预防策略", "应急措施", "措施", "原则", "处置原则",
            "分析", "分析方法", "分析目标", "分析结果", "复盘内容",
            "日志", "日志类型", "日志级别", "日志内容", "日志文件", "日志路径", "日志采集",
            "数据", "数据类型", "数据对象", "数据结构", "数据源", "字段", "属性",
            "状态", "类别", "类型", "知识类型", "知识条目", "知识编号", "知识库",
            "文档", "文档类型", "文档编号", "文档名称", "手册", "章节", "子章节", "章节条款",
            "内容", "目录", "列表", "清单", "报表", "凭证", "台账",
            "角色", "人员", "人员类型", "责任人", "部门", "组织", "团队",
            // 电信/产品营销域高频类型（来自小微ICT等知识库的生产数据归纳）：
            // 缺失时 LLM 输出的"套餐/产品/客户/补贴"等核心类型全部落 UNKNOWN
            "产品", "套餐", "礼包", "产品包", "标准包", "标品", "资费", "价格", "费用", "折扣",
            "优惠", "补贴", "权益", "激励", "客户", "客户类型", "客群", "案例", "品牌", "渠道",
            "营销", "营销方法", "合同", "订单", "工单", "报价", "报价单", "计费", "结算",
            "网络", "专线", "宽带", "组网", "维保", "售后", "施工", "机房",
            "目标", "要求", "标准", "规范", "规则", "概念", "场景", "环境类型", "事件",
            "对象", "资源", "资源类型", "工具", "功能", "功能模块", "结果", "输出结果", "校验结果",
            "验证方式", "验证对象", "业务", "业务领域", "业务含义",
            "代码", "代码实现", "代码问题", "模型", "版本", "版本迭代", "主题",
            "参考依据", "资料", "说明", "Entity"
    )));

    private EntityTypeNormalizer() {}

    /**
     * 归一实体类型（使用默认白名单）
     */
    public static String normalize(String type) {
        return normalize(type, DEFAULT_WHITELIST);
    }

    /**
     * 归一实体类型：精确命中（忽略大小写）→ 编辑距离 ≤1 归并 → UNKNOWN
     */
    public static String normalize(String type, Collection<String> whitelist) {
        if (type == null || type.isBlank()) return UNKNOWN;
        String t = type.trim();
        if (whitelist == null || whitelist.isEmpty()) return UNKNOWN;

        for (String w : whitelist) {
            if (w.equalsIgnoreCase(t)) return w;
        }
        // 编辑距离 ≤1 归并（如 "故障类别"→"故障类型"、"监控项 "→"监控项"），避免同义类型分裂
        for (String w : whitelist) {
            if (Math.abs(w.length() - t.length()) <= 1 && levenshtein(w, t) <= 1) {
                return w;
            }
        }
        return UNKNOWN;
    }

    /** 编辑距离（Levenshtein），供类型归并用 */
    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
