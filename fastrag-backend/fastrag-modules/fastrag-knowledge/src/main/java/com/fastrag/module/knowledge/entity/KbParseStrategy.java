package com.fastrag.module.knowledge.entity;
/**
 * 知识库解析策略实体类，对应数据库表 kb_parse_strategy。
 *
 * <p>核心职责：
 * 定义知识库的文档解析策略，包括适用的文件扩展名列表、解析方法、
 * 高级配置参数以及模型选择。每个知识库可拥有多个解析策略，其中
 * 一个可设为默认策略（isDefault=1）。解析策略在文件摄入时通过
 * StrategyConfigResolver 解析匹配，决定文档的解析方式。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>fileId — 文件级绑定（非空 = 该文件的专属自定义策略，不出现在策略管理列表；
 *       空 = 知识库级命名策略）。文件级策略由「换策略重新分片」对话框的自定义面板创建/更新，
 *       随文件删除级联清理</li>
 *   <li>name — 策略名称</li>
 *   <li>description — 策略描述</li>
 *   <li>extensions — JSON 数组，适用的文件扩展名列表（如 [".pdf", ".docx"]）</li>
 *   <li>parseMethod — 解析方法（文档类型）：default（自动识别）/ pdf / doc / docx / pptx / xlsx / video / audio / image</li>
 *   <li>isDefault — 是否为默认策略（0=否，1=是）</li>
 *   <li>advanced — JSON 格式的高级解析配置（含 parse、chunk、index 三个分组）</li>
 *   <li>llmModel — 解析过程使用的 LLM 大语言模型</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_parse_strategy")
public class KbParseStrategy {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String kbId;
    /** 文件级绑定：非空 = 该文件的专属自定义策略（不出现在策略管理，随文件删除级联清理） */
    private String fileId;
    private String name;
    private String description;
    private String extensions; // JSON array
    private String parseMethod; // default / pdf / doc / docx / pptx / xlsx / video / audio / image
    private Integer isDefault;
    private String advanced; // JSON
    private String llmModel; // 解析用 LLM 模型
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
