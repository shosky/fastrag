package com.fastrag.module.tools.entity;

/**
 * 技能文件树节点 DTO，用于前端文件浏览器展示。
 *
 * <p>非数据库实体，采用递归树结构（children 自引用），
 * 每个节点代表技能目录下的一个文件或子目录。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code name} - 文件/目录名</li>
 *   <li>{@code isDir} - 是否为目录</li>
 *   <li>{@code size} - 文件大小（字节）</li>
 *   <li>{@code lastModified} - 最后修改时间</li>
 *   <li>{@code children} - 子节点列表（目录时递归展开）</li>
 * </ul>
 *
 * @see SkillFileContent
 */
import lombok.Data;

import java.util.List;

@Data
public class SkillFileTree {
    private String name;
    private boolean isDir;
    private long size;
    private long lastModified;
    private List<SkillFileTree> children;
}
