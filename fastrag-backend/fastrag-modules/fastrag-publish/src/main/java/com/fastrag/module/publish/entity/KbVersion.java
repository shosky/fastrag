package com.fastrag.module.publish.entity;

/**
 * 知识库版本实体类。
 *
 * <p>对应数据库表 {@code kb_version}，管理知识库的版本生命周期，
 * 包括草稿、审核中、已通过、已发布、已驳回等状态。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code kbId} - 关联的知识库ID</li>
 *   <li>{@code name} - 版本名称</li>
 *   <li>{@code description} - 版本描述</li>
 *   <li>{@code publishStatus} - 发布状态（draft / pending_review / approved / published / rejected）</li>
 *   <li>{@code changeSummary} - 变更摘要</li>
 *   <li>{@code version} - 版本号（整数，自动递增）</li>
 *   <li>{@code fileCount} - 包含的文件数量</li>
 *   <li>{@code chunkCount} - 包含的分块数量</li>
 *   <li>{@code tags} - 版本标签（JSON 字符串，通过 Jackson 序列化/反序列化）</li>
 *   <li>{@code createdBy} - 创建人</li>
 * </ul>
 *
 * @see com.fastrag.module.publish.mapper.KbVersionMapper
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("kb_version")
public class KbVersion {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, name, description, publishStatus, changeSummary, createdBy;
    @JsonIgnore private String tags;
    private Integer version, fileCount, chunkCount;
    private LocalDateTime createdAt;

    @JsonProperty
    public void setTags(Object tags) {
        if (tags == null) this.tags = null;
        else if (tags instanceof CharSequence) this.tags = tags.toString();
        else try { this.tags = MAPPER.writeValueAsString(tags); } catch (Exception e) { this.tags = tags.toString(); }
    }
}
