package com.fastrag.module.tools.entity;

/**
 * 技能文件内容 DTO，用于前端文件编辑和预览。
 *
 * <p>非数据库实体，仅作为 API 响应对象使用。
 * 包含文件路径、内容、大小、最后修改时间和是否存在等文件元信息。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code path} - 文件相对路径</li>
 *   <li>{@code content} - 文件文本内容</li>
 *   <li>{@code size} - 文件大小（字节）</li>
 *   <li>{@code lastModified} - 最后修改时间</li>
 *   <li>{@code exists} - 文件是否存在</li>
 *   <li>{@code error} - 错误信息（读取失败时）</li>
 * </ul>
 *
 * @see SkillFileTree
 */
import lombok.Data;

@Data
public class SkillFileContent {
    private String path;
    private String content;
    private long size;
    private long lastModified;
    private boolean exists;
    private String error;
}
