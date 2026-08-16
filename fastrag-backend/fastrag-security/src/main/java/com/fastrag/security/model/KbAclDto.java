package com.fastrag.security.model;
/**
 * 知识库访问控制列表（ACL）数据传输对象，用于承载知识库级别的用户授权信息。
 *
 * <p>核心职责：在知识库权限管理接口中传递 ACL 记录数据，描述某个用户在某个知识库上被授予的角色。
 *
 * <p>包含的字段：
 * <ul>
 *   <li>{@code id} — ACL 记录主键</li>
 *   <li>{@code kbId} — 知识库 ID</li>
 *   <li>{@code userId} — 被授权的用户 ID</li>
 *   <li>{@code userName} — 被授权的用户名（冗余存储，方便展示）</li>
 *   <li>{@code grantedBy} — 授权人用户 ID（谁授予的该权限）</li>
 *   <li>{@code kbRole} — 授予的角色（owner / editor / viewer，对应 {@link com.fastrag.common.enums.KBRole}）</li>
 *   <li>{@code grantedAt} — 授权时间</li>
 * </ul>
 *
 * <p>与其他模块的交互：被 {@link com.fastrag.security.service.KbAclService} 接口的方法作为参数和返回值使用，
 * ACL 数据的持久化由 knowledge 模块中的 KbAclService 实现类完成。
 */
import com.fastrag.common.enums.KBRole; import lombok.Data; import java.time.LocalDateTime;
@Data public class KbAclDto { private Long id; private String kbId,userId,userName,grantedBy; private KBRole kbRole; private LocalDateTime grantedAt; }
