package com.fastrag.module.application.entity;

/**
 * 应用实体类，对应数据库表 {@code app}，是应用中心模块的核心实体。
 *
 * <p>每个 {@code App} 代表平台中创建的一个智能应用实例，包含应用的基本元信息，
 * 如名称、描述、类型、图标、状态、所属者和所属组织等。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code id} — 应用唯一标识，使用雪花算法生成</li>
 *   <li>{@code name} — 应用名称</li>
 *   <li>{@code description} — 应用描述</li>
 *   <li>{@code type} — 应用类型（如 chat_agent、workflow 等）</li>
 *   <li>{@code icon} — 应用图标</li>
 *   <li>{@code status} — 应用状态（如 draft、published、archived）</li>
 *   <li>{@code owner} — 应用创建者/拥有者ID</li>
 *   <li>{@code orgId} — 所属组织ID，用于组织级数据权限隔离</li>
 *   <li>{@code tags} — 应用标签，JSON格式存储，序列化时隐藏</li>
 *   <li>{@code createdAt / updatedAt} — 创建和更新时间</li>
 * </ul>
 *
 * <p>与其他实体的关系：通过 {@code appId} 与 {@link AppBasicConfig}、{@link AppConfig}、
 * {@link AppDialogConfig}、{@link AppConversation} 等子实体关联，形成一对多关系。
 * 被 {@link AppController} 和 {@link AppConfigController} 操作，
 * 被 {@link com.fastrag.module.application.mapper.AppMapper} 持久化。
 *
 * @see com.fastrag.module.application.controller.AppController
 * @see com.fastrag.module.application.service.AppService
 */
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app") public class App {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,type,icon,status,owner;
    private String orgId; // 归属组织（同组织可见，创建时写入）
    @JsonIgnore private String tags;
    private LocalDateTime createdAt,updatedAt;

    @JsonProperty public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
}
