-- ===========================================================================
-- 技能管理系统数据库迁移脚本
-- 新增列：skill.share_config, mcp_service.description
-- 
-- 注意：应用此脚本后，需移除 Skill.java 和 McpService.java 中对应
-- @TableField(exist = false) 注解，然后重新编译部署。
-- ===========================================================================

-- 1. skill 表新增 share_config 列
ALTER TABLE skill 
  ADD COLUMN `share_config` JSON DEFAULT NULL COMMENT '分享配置 JSON：{accessLevel, departmentIds[], userUids[]}';

-- 2. mcp_service 表新增 description 列
ALTER TABLE mcp_service 
  ADD COLUMN `description` VARCHAR(512) DEFAULT NULL COMMENT 'MCP 服务描述';

-- 3. 为 skill 表补充索引（提升按 slug 查询性能）
ALTER TABLE skill 
  ADD INDEX idx_skill_slug (slug),
  ADD INDEX idx_skill_source_type (source_type),
  ADD INDEX idx_skill_enabled (enabled);

-- 4. 为 skill_dependency 表补充索引
ALTER TABLE skill_dependency 
  ADD INDEX idx_skill_dep_skill_id (skill_id);

-- 5. 为 skill_scope 表补充索引
ALTER TABLE skill_scope 
  ADD INDEX idx_skill_scope_skill_id (skill_id);

-- 6. 内置技能种子数据初始化的标记
-- 说明：应用启动后，SkillDataInitializer 会自动从 classpath:builtin-skills/
-- 解析 SKILL.md 并写入 skill 表。首次部署时技能表为空是正常现象。

-- ===========================================================================
-- 回滚脚本（如需）
-- ===========================================================================
-- ALTER TABLE skill DROP COLUMN `share_config`;
-- ALTER TABLE mcp_service DROP COLUMN `description`;
-- DROP INDEX idx_skill_slug ON skill;
-- DROP INDEX idx_skill_source_type ON skill;
-- DROP INDEX idx_skill_enabled ON skill;
-- DROP INDEX idx_skill_dep_skill_id ON skill_dependency;
-- DROP INDEX idx_skill_scope_skill_id ON skill_scope;
