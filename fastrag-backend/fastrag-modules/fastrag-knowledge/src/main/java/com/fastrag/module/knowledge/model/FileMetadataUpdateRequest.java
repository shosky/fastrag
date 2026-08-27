package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.util.Map;

/**
 * 文件元数据更新请求（分册四 · schema 驱动版本）。
 *
 * <p>全量提交该文件所有元数据字段的取值：key 对应 KB 自定义属性 (AttrDef) 的 name，
 * 未在 schema 中定义的 key 将被忽略；其中检索感知字段（region/publishDate/docLevel，勾选
 * searchable）由服务端按保留名投影到 kb_file 强类型列，其余字段写入 custom_attrs JSON。
 * 标签走独立接口 PUT /tags。保存后 metadata_status 置为 revised、metadata_source=manual。</p>
 */
@Data
public class FileMetadataUpdateRequest {
    /** 字段取值映射：key = AttrDef.name，value = 该字段的值（null/空=清空） */
    private Map<String, Object> values;
}