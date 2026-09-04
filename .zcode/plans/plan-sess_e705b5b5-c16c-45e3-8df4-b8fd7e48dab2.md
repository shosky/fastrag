# 实现 rag-file-metadata-management.md（P0 + P1，不含 P2 检索消费）

实现文档「文档元数据管理设计（分册四）」的管理能力闭环。P2（自定义属性进检索过滤）依赖分册二，本仓检索消费尚未实现，只预留接口占位，不在本 pass 实现。

## 1. 数据层

### 1.1 新增迁移脚本 `fastrag-backend/init-scripts/migration-20260820-file-metadata.sql`
按现有 migration 风格（information_schema 存在性检查 + PREPARE/EXECUTE）：
- `kb_file` 增列：`region VARCHAR(64)`、`publish_date DATE`、`doc_level VARCHAR(16)`、`issuer VARCHAR(128)`、`doc_number VARCHAR(64)`、`metadata_status VARCHAR(16) DEFAULT 'none'`、`custom_attrs JSON`、`metadata_source VARCHAR(16)`；加索引 `idx_kb_file_region / idx_kb_file_doc_level / idx_kb_file_publish_date`。
- `kb` 增列 `custom_attr_schema JSON`。
- `kb_chunk` 增冗余列 `region / publish_date / doc_level` + 索引（与分册二定义一致）。
- 同步在 `schema.sql` 主建表脚本中补上对应列（新建库路径），保持两处一致。

### 1.2 实体扩展（MyBatis-Plus 注解零 XML，camelCase↔snake_case 自动映射）
- `entity/KbFile.java`：新增 region、publishDate、docLevel、issuer、docNumber、metadataStatus、customAttrs(String)、metadataSource 字段及 Javadoc。
- `entity/KnowledgeBase.java`：新增 customAttrSchema(String JSON) 字段。
- `entity/KbChunk.java`：新增 region、publishDate、docLevel 冗余字段。

## 2. 后端模型/DTO（fastrag-knowledge/module/knowledge/model）
- 新增 `FileMetadataVO.java`：固定字段五元组 + metadataStatus + metadataSource + `List<FileTagVO>`(id/name/color/tagTypeId) + `List<Map<String,Object>>` customAttrs + `List<AttrDef>` customAttrSchema（来自 KB）+ 文件名/文件ID。
- 新增 `AttrDef.java`：name/type/text|number|select|date|boolean/options/required/description/defaultValue。
- 新增 `FileMetadataUpdateRequest.java`：region/publishDate/docLevel/issuer/docNumber/customAttrs(Map)/(可选 tagIds 或独立接口)。
- 新增 `FileBatchTagRequest.java`：fileIds + addTagIds + removeTagIds。
- `FileDto.java`：新增元数据摘要字段 region/docLevel/publishDate/metadataStatus/tags(List<String> 标签名)；`toDto()` 批量加载标签（一次 relation 查询 + 一次 tag 查询，避免 N+1）。

## 3. 服务层（fastrag-knowledge/module/knowledge/service）
- 新增 `FileMetadataService` 接口 + `impl/FileMetadataServiceImpl.java`：
  - `getMetadata(kbId, fileId)` → FileMetadataVO（组装固定字段 + 标签 + KB schema + 取值）。
  - `updateMetadata(kbId, fileId, req)`：@Transactional 写固定字段 + customAttrs JSON；状态机：人工写入→`metadata_status=revised`、`metadata_source=manual`；事务内回填 `kb_chunk` 冗余列（region/publish_date/doc_level）；不触发 re-chunk、不动 embedding。
  - `setFileTags(kbId, fileId, tagIds)`：替换式设置，维护 `kb_tag_relation(targetType='file')` + 标签 `usage_count` 增减。
  - `batchSetTags(kbId, fileIds, addTagIds, removeTagIds)`：批量加/删标签，usage_count 正确增减，事务内完成。
  - `extractMetadata(kbId, fileIds)`：规则抽取（行政区划词典 + 文号/日期正则 + 发文层级关键词 + 文号正则，从文件名抽取，可选从文件名+已入库文本），仅覆盖 `none/partial`，成功 5 字段全定→`full`、部分→`partial`、失败→`partial`，`metadata_source=auto`；回填 chunk；返回成功/失败计数。
- `FileService` 接口 + `FileServiceImpl`：
  - 提取标签关系辅助方法（按 targetType 增删 relation、usage_count 维护）供 KbService 与 FileMetadataService 复用（放入公共 helper 或 FileMetadataService 静态工具）。
  - `update()` 保持不变（name/folderId/enableGraphBuild），元数据走专用接口不混入（符合 ADR-0001 精神；不破坏 A9 回归）。
  - `copy`/`moveToKb` 携带元数据列（copy 复制；move 保留）。
- `KbService` + `impl/KbServiceImpl`：
  - `syncTagRelations` 改造为支持 `targetType` 参数（'kb'/'file' 复用同一套逻辑）。
  - `update`/`create` 接受 `customAttrSchema` 落库；`toDto` 输出 customAttrSchema。
- `StorageServiceImpl.batchInsertChunks` / `batchInsertParents`：入库时把文件的 region/publishDate/docLevel 冗余到 chunk（从 fileMapper 已查出的 KbFile 取值）。

## 4. 控制器
- `FileController`：新增
  - `GET /{id}/metadata`（viewer）→ FileMetadataVO
  - `PUT /{id}/metadata`（editor，@Loggable file_updated）→ 更新固定字段+自定义属性
  - `PUT /{id}/tags`（editor）→ body `{tagIds:[...]}` 替换式设置
  - `POST /batch-tags`（editor）→ body `{fileIds,addTagIds,removeTagIds}` 批量打标
  - `POST /metadata/extract`（editor）→ 存量批量规则抽取
- `KbTagController`（`/api/kb-tags`）：新增
  - `POST` 创建标签（body 含 kbId/name/color/tagTypeId/description；校验所在 KB editor 权限，注入 KbAccessChecker+SecurityUtil 手动校验，API token 放行）
  - `PUT /{id}`、`DELETE /{id}`（校验该标签归属 KB 的 editor 权限；删除时清理 relation 关联并按 relation 减 usage_count；usage_count 归零后才允许删除）
- `KbController.update`/`create`：透传 customAttrSchema。

## 5. 前端

### 5.1 类型 `src/types/knowledge.ts`
- `KnowledgeFile` 增加可选字段 region/publishDate/docLevel/issuer/docNumber/metadataStatus/metadataSource/tags(string[])/customAttrs?。
- 新增 `AttrDef`、`FileMetadata`（VO 形状）、`KbTag`、`FileTagVO` 类型。

### 5.2 API `src/api/index.ts`
- `getFileMetadata(kbId,fileId)`、`updateFileMetadata(...)`、`setFileTags(...)`、`batchSetFileTags(...)`、`extractFileMetadata(...)`。
- `createKbTag / updateKbTag / deleteKbTag / getKbTags(kbId?)`。

### 5.3 新组件 `src/views/knowledge/detail/components/FileMetadataDialog.vue`
- 遵循现有 dialog 约定（props visible/file/kbId，emit update:visible + saved，el-dialog + destroy-on-close，BEM SCSS）。
- 三区：固定字段表单（region 多值输入、publishDate 日期选择、docLevel 下拉、issuer、docNumber）；标签区（KB 标签多选 + 新建标签快捷入口）；自定义属性区（按 KB customAttrSchema 渲染 text/number/select/date/boolean 表单）。
- 展示 metadata_status / metadata_source，提供「保存为已校订」语义（保存即 revised）。

### 5.4 `FileTable.vue`
- 新增「标签」列（el-tag 展示前 2 个 + "+n"）与「元数据」列（region 或日期等摘要 + 状态标签）。
- 行操作菜单增加「元数据」入口（emit `metadata`）。
- 批量操作栏增加「批量打标」按钮（emit `batchTag`）；选中文件时显示。
- 分页对齐 AGENTS.md 统一规范：`layout="total, sizes, prev, pager, next, jumper"`、`:page-sizes="[10,20,50,100]"`、BEM 容器样式。

### 5.5 `FileManager.vue`
- 接入 FileMetadataDialog、批量打标对话框（复用 FileMetadataDialog 的标签选择或独立小对话框）、事件接线与保存后刷新。

### 5.6 `form.vue`（customAttrs 复活）
- 保留现有 UI 骨架，补充 `options`（select 时逗号分隔编辑）；编辑模式从 initialData.customAttrSchema 回填。
- `handleSubmit` payload 增加 `customAttrSchema`（过滤空行 name）。
- `KnowledgeBase` 类型增加 customAttrSchema。

### 5.7 标签管理页 `src/views/admin/content/tags.vue`
- 增加「知识库标签」Tab（接入 `/api/kb-tags` CRUD），支持按 KB 过滤、颜色、T1/T2/T3 类型选择，删除前确认提示影响文件数。

## 6. 验收清单对照（文档第 8 节）
- A1 元数据可读：GET metadata 返回全量字段+标签+自定义属性。
- A2 元数据可写 + chunk 回填 + 事务。
- A3 标签 CRUD + 文件打标/去标 + usage_count + file/kb 互不干扰。
- A4 批量打标 + usage_count。
- A5 权限：写接口 @KbAuth(editor) 拦截；KbTagController 写操作手动 ACL 校验。
- A6 customAttrs 复活：KB 保存 schema 落库；文件详情按 schema 渲染并保存。
- A7 校订闭环：人工保存→revised，抽取不覆盖 revised。
- A8 存量补抽：extract 规则抽取回填，失败 partial，不中断批量。
- A9 回归：update()/检索路径不动。

## 7. 验证
- 后端：`mvn -pl fastrag-modules/fastrag-knowledge -am compile`（含依赖模块）编译通过；如可行运行相关单测。
- 前端：`npm run build`（vue-tsc -b 类型检查 + vite build）通过。
- 手工走查验收项对应的接口返回结构。

## 风险与注意
- 仓库当前有未提交的已存在改动（基线分支），实施时不动无关文件。
- KbTagController 写端点不在 `/api/kb/{kbId}` 路径下，KbAuthAspect 无法自动取 kbId，采用注入 accessChecker 手动校验（与 KbAuthAspect 同策略，API token 放行）。
- 标签删除涉及 usage_count 与 relation 清理，需在事务内完成并处理「标签被 file 和 kb 同时引用」的场景。