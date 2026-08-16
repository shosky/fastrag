package com.fastrag.module.platform.entity;

/**
 * 敏感词实体
 * <p>
 * 对应数据库表 {@code sensitive_word}，存储系统内容安全审核所需的敏感词配置。
 * 每个敏感词条目包含词语、替换文本、分类策略（category JSON）和敏感级别。
 * 分类策略支持三种拦截方式：阻止用户输入（blockInput）、联网检索屏蔽（blockSearch）、
 * 模型生成答案时替换（replaceAnswer），以 JSON 字符串存储。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code word} — 敏感词文本</li>
 *   <li>{@code replacement} — 替换文本（用于 replaceAnswer 策略时替换敏感词）</li>
 *   <li>{@code category} — 分类策略JSON（包含 blockInput、blockSearch、replaceAnswer 布尔标志）</li>
 *   <li>{@code level} — 敏感级别（如 high、medium、low）</li>
 *   <li>{@code enabled} — 启用状态（1=启用，0=禁用）</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.SensitiveWordService
 * @see com.fastrag.module.platform.mapper.SensitiveWordMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sensitive_word") public class SensitiveWord {
    @TableId(type=IdType.AUTO) private Long id;
    private String word,category,level,replacement;
    private Integer enabled;
    private LocalDateTime createdAt;
}
