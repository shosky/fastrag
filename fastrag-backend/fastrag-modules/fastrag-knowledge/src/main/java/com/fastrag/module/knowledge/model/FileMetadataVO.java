package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 文件元数据视图对象（分册四 · schema 驱动版本）。
 *
 * <p>返回单个文件的完整元数据：由 KB 自定义属性 schema 决定字段集合，{@code values} 为该文件
 * 所有字段的取值（含检索感知字段投影到 kb_file 强类型列后的值 + 普通字段 custom_attrs 值，按
 * AttrDef.name 归并）。GET /api/kb/{kbId}/files/{id}/metadata 返回此对象。</p>
 */
@Data
public class FileMetadataVO {
    private String fileId;
    private String fileName;

    /** KB 级元数据字段定义（用户自定义，含 label/type/options/required/searchable） */
    private List<AttrDef> attrSchema;

    /** 该文件的全部字段取值：key = AttrDef.name；缺失 key = 未填 */
    private Map<String, Object> values;

    // ===== 状态（管理侧扩展） =====
    private String metadataStatus;  // none/partial/full/revised
    private String metadataSource;  // manual/auto/mixed

    // ===== 标签（L2，targetType='file'） =====
    private List<FileTagVO> tags;

    /** 标签聚合视图 */
    @Data
    public static class FileTagVO {
        private String id;
        private String name;
        private String color;
        private String tagTypeId;
    }
}