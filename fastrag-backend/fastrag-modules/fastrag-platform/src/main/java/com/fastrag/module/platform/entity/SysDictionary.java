package com.fastrag.module.platform.entity;

/**
 * 系统数据字典实体
 * <p>
 * 对应数据库表 {@code sys_dictionary}，存储系统各类枚举值、下拉选项等配置型数据。
 * 字典数据按 dictType 分类组织，每个类型下包含多条 key-value 映射。
 * 前端通过字典接口获取格式化的下拉选项数据。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code dictType} — 字典类型（如 model_brand、model_purpose、sensitive_level 等）</li>
 *   <li>{@code dictKey} — 字典项键（显示文本/标签）</li>
 *   <li>{@code dictValue} — 字典项值（实际存储值）</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.DictionaryService
 * @see com.fastrag.module.platform.mapper.SysDictionaryMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("sys_dictionary") public class SysDictionary {
    @TableId(type=IdType.AUTO) private Long id;
    private String dictType,dictKey,dictValue;
}
