package com.fastrag.module.publish.entity;
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
