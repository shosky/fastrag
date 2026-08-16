package com.fastrag.security.service;
/**
 * 知识库访问控制列表（ACL）管理服务接口，定义知识库权限的查询和管理契约。
 *
 * <p>核心职责：提供知识库级别用户权限的增删查能力，支持 owner/editor/viewer 三级角色体系。
 * 接口定义在 security 模块（公共契约），实现在 knowledge 模块（依赖数据库和缓存）。
 *
 * <p>提供的方法：
 * <ul>
 *   <li>{@code getKbAcl} — 查询指定知识库的 ACL 列表</li>
 *   <li>{@code setKbAcl} — 批量设置知识库的 ACL（全量覆盖）</li>
 *   <li>{@code addAclEntry} — 添加单条 ACL 授权记录</li>
 *   <li>{@code removeAclEntry} — 移除指定用户在知识库上的 ACL 授权</li>
 *   <li>{@code getAccessibleKbIds} — 查询用户可访问的所有知识库 ID</li>
 *   <li>{@code getKbRole} — 查询指定用户在指定知识库上的角色</li>
 * </ul>
 *
 * <p>与其他模块的交互：被知识库模块的 Controller 层调用，提供权限管理 REST API 的后端实现；
 * {@link KbAccessChecker} 的实现类在判定权限时会读取 ACL 数据。
 */
import com.fastrag.common.enums.KBRole; import com.fastrag.security.model.KbAclDto; import java.util.List;
public interface KbAclService { List<KbAclDto> getKbAcl(String kbId); void setKbAcl(String kbId,List<KbAclDto> entries); void addAclEntry(String kbId,String userId,KBRole role,String grantedBy); void removeAclEntry(String kbId,String userId); List<String> getAccessibleKbIds(String userId); KBRole getKbRole(String userId,String kbId); }
