package com.fastrag.common.base;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类，为所有业务实体提供统一的通用字段。
 * <p>所有需要持久化到数据库的业务实体类均应继承此基类，确保具有一致的主键策略和时间审计字段。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code id} - 主键标识，使用 32 位字符串（通常由 {@link com.fastrag.common.util.IdGenerator} 生成的 UUID）</li>
 *   <li>{@code createdAt} - 创建时间，记录实体首次入库的时间</li>
 *   <li>{@code updatedAt} - 更新时间，记录实体最后一次修改的时间</li>
 * </ul>
 *
 * <p>设计意图：通过抽象基类统一实体的身份标识和审计信息，避免在每个实体中重复定义。
 * 使用 Lombok 的 {@code @Data} 注解自动生成 getter/setter，配合 MyBatis-Plus 或手动映射时
 * 需要注意这些字段在数据库表中的列名对应关系（通常为 created_at、updated_at）。
 *
 * <p>继承关系：本类实现 {@code Serializable} 接口，支持对象的序列化传输（如缓存、远程调用等场景）。
 */
@Data
public abstract class BaseEntity implements Serializable {
    private String id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
