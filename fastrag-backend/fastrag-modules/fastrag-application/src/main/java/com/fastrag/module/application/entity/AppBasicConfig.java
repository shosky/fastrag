package com.fastrag.module.application.entity;

/**
 * 应用基础配置实体类，对应数据库表 {@code app_basic_config}。
 *
 * <p>存储应用的基础运行参数和用户界面相关配置，每个应用对应一条基础配置记录，
 * 通过 {@code appId} 与 {@link App} 实体形成一对一关系。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code id} — 配置记录唯一标识</li>
 *   <li>{@code appId} — 关联的应用ID</li>
 *   <li>{@code outputFormat} — 输出格式（如 markdown、text、json）</li>
 *   <li>{@code responseLanguage} — 响应语言</li>
 *   <li>{@code greeting} — 开场白/欢迎语</li>
 *   <li>{@code goodbyeMessage} — 结束语</li>
 *   <li>{@code advancedOptions} — 高级选项，JSON格式存储</li>
 *   <li>{@code updatedBy} — 最后更新人</li>
 *   <li>{@code memoryRounds} — 记忆轮数，控制对话上下文保留的轮数</li>
 *   <li>{@code timeoutSeconds} — 超时时间（秒）</li>
 *   <li>{@code maxInputLength} — 最大输入长度限制</li>
 * </ul>
 *
 * <p>由 {@link com.fastrag.module.application.controller.AppConfigController} 的基础配置端点进行读写，
 * 被 {@link com.fastrag.module.application.mapper.AppBasicConfigMapper} 持久化。
 *
 * @see App
 * @see com.fastrag.module.application.service.AppConfigService#getBasic(String)
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_basic_config") public class AppBasicConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,outputFormat,responseLanguage,greeting,goodbyeMessage,advancedOptions,updatedBy;
    private Integer memoryRounds,timeoutSeconds,maxInputLength; private LocalDateTime createdAt,updatedAt;
}
