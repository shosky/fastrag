# 模块五：发布审核与日志

> 对应前端：`PublishPanel.vue`、`LogPanel.vue`、`review-center.vue`、文件操作日志
> 数据契约：`src/mock/audit.ts`、`src/mock/kb-logs.ts`、`src/mock/knowledge-update.ts`、`src/types/audit.ts`

---

## 一、业务概述

知识库的版本治理与变更可观测。包含三块：

| 子域 | 能力 |
|------|------|
| 版本发布 | 创建版本快照、版本列表、最新/已发布版本 |
| 发布状态机 | draft → pending_review → approved/rejected → published 的流转 |
| 审核 | 提交审核、待审列表、批准/拒绝（带权限） |
| 统一日志 | 操作日志 / 检索日志 / 发布日志 三类合一，按 KB 隔离 |

### 1.1 发布状态机（对齐 `PUBLISH_TRANSITIONS`）

```
draft ──提交──▶ pending_review ──批准──▶ approved ──发布──▶ published
                       │                      │
                       └──拒绝──▶ rejected ──重提──▶ draft
                                  published ──新建版本──▶ draft
```

| 当前状态 | 允许流转到 |
|----------|-----------|
| draft | pending_review |
| pending_review | approved, rejected |
| approved | published |
| published | draft |
| rejected | draft |

### 1.2 统一日志三类（对齐 `LogCategory`）

| category | 写入方 | 内容 |
|----------|--------|------|
| operation | 文件/分片/配置变更（模块2） | rename/move/copy/delete/restore 等 |
| retrieval | 检索 API（模块3） | query/mode/topK/hits/duration |
| publish | 版本发布/审核（本模块） | 创建发布/提交审核/批准/拒绝/发布 |

---

## 二、数据模型（Entity）

### 2.1 `kb_version` 版本快照（对齐 `KnowledgeVersion`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| kb_id | varchar(32) | |
| version | int | 自增版本号 |
| name / description | varchar | |
| tags | varchar(512) | |
| file_count / chunk_count | int | 快照统计 |
| publish_status | enum('draft','pending_review','approved','published','rejected') | |
| change_summary | text | 变更摘要 |
| created_by | varchar(32) | |
| created_at | datetime | |

### 2.2 `kb_review_task` 审核任务（对齐 `ReviewTask`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| kb_id / kb_name | varchar | |
| version_id / version | varchar/int | |
| applicant | varchar(32) | 申请人 |
| reviewer | varchar(32) | 审核人 |
| status | enum('pending','approved','rejected','timeout') | |
| comment | text | 审核意见 |
| created_at / reviewed_at | datetime | |

### 2.3 `kb_log` 统一日志（对齐 `KBLog`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| kb_id | varchar(32) | 索引 |
| category | enum('operation','retrieval','publish') | |
| action | varchar(64) | 动作 |
| target | varchar(255) | 操作对象 |
| detail | text | 详情 |
| operator | varchar(32) | 操作人/调用方 |
| status | varchar(32) | 状态（如 success/error/已通过） |
| extra | json | 扩展（query/mode/hits 等） |
| timestamp | datetime | |

### 2.4 `kb_update_log` 变更记录（对齐 `UpdateLog`）

记录细粒度变更，用于版本 diff：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| kb_id | varchar(32) | |
| update_type | enum('file_added','file_removed','file_updated','chunk_added','chunk_removed','chunk_updated','config_changed') | |
| target / detail | varchar/text | |
| old_value / new_value | text | |
| operator | varchar(32) | |
| timestamp | datetime | |

---

## 三、接口设计（API）

### 3.1 版本发布

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/kb/{kbId}/versions` | 版本列表 | KB viewer |
| GET | `/kb/{kbId}/versions/latest` | 最新版本 | KB viewer |
| GET | `/kb/{kbId}/versions/published` | 已发布版本 | KB viewer |
| POST | `/kb/{kbId}/versions` | 创建版本（draft）`{ name, description, tags }` | KB editor |
| POST | `/kb/{kbId}/versions/{id}/transition` | 状态流转 `{ targetStatus }` | KB editor |

### 3.2 审核

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/reviews` | 审核列表（可选 `?kbId=`） | `review:read` |
| GET | `/reviews/pending` | 待审列表 | `review:approve` |
| POST | `/reviews` | 提交审核 `{ kbId, versionId, applicant }` | KB editor |
| POST | `/reviews/{id}/approve` | 批准 `{ comment }` | `review:approve` |
| POST | `/reviews/{id}/reject` | 拒绝 `{ comment }` | `review:reject` |

### 3.3 日志

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/kb/{kbId}/logs` | 统一日志列表（可选 `?category=`） |
| GET | `/kb/{kbId}/logs?category=operation\|retrieval\|publish` | 按类别过滤 |

### 3.4 变更记录

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/kb/{kbId}/update-logs` | 变更记录 |
| GET | `/kb/{kbId}/update-logs?type=` | 按类型过滤 |
| GET | `/kb/{kbId}/update-logs/diff?from=&to=` | 版本间 diff `{ added, removed, updated, logs[] }` |

---

## 四、核心逻辑设计

### 4.1 状态机流转（对齐 `transitionStatus`）

```java
@Service
public class PublishService {
    private static final Map<PublishStatus, Set<PublishStatus>> TRANSITIONS = Map.of(
        DRAFT, Set.of(PENDING_REVIEW),
        PENDING_REVIEW, Set.of(APPROVED, REJECTED),
        APPROVED, Set.of(PUBLISHED),
        PUBLISHED, Set.of(DRAFT),
        REJECTED, Set.of(DRAFT)
    );

    @Transactional
    public KnowledgeVersion transition(String kbId, String versionId, PublishStatus target, String operator) {
        KnowledgeVersion v = versionMapper.selectById(versionId);
        if (!TRANSITIONS.get(v.getPublishStatus()).contains(target)) {
            throw new BizException("不允许从 " + v.getPublishStatus() + " 流转到 " + target);
        }
        v.setPublishStatus(target);
        versionMapper.update(v);
        // 写发布日志
        logService.addPublishLog(kbId, target.getAction(), v.getName(),
            "状态流转：" + v.getPublishStatus() + "→" + target, target.getLabel(), operator);
        return v;
    }
}
```

### 4.2 审核批准/拒绝（对齐 `approveReview`/`rejectReview`）

```java
public ReviewTask approve(String reviewId, String comment) {
    perm.check("review:approve");  // 权限校验
    ReviewTask t = reviewMapper.selectById(reviewId);
    if (t.getStatus() != PENDING) throw new BizException("非待审任务");
    t.approve(SecurityUtil.currentUserId(), comment);
    reviewMapper.update(t);
    // 触发版本状态机
    publishService.transition(t.getKbId(), t.getVersionId(), APPROVED, t.getReviewer());
    return t;
}
```

### 4.3 统一日志写入（三入口）

日志是「被写入」而非主动管理，三个写入入口对齐前端：

| 入口 | 调用时机 | 示例 |
|------|----------|------|
| `addOperationLog` | 模块2 文件/分片/配置变更 | `rename` 文件A → 文件B |
| `addRetrievalLog` | 模块3 检索 API | query=小微ICT, mode=hybrid, hits=5 |
| `addPublishLog` | 本模块版本/审核 | 提交审核 v3 |

统一落 `kb_log` 表，按 `category` 区分。查询时按 `kb_id` 过滤、按 `timestamp` 倒序。

### 4.4 版本 diff（对齐 `getVersionDiff`）

聚合两个版本间的 `kb_update_log`，统计 `added/removed/updated` 数量，返回明细列表。

---

## 五、前端覆盖核对表

| 前端 mock 函数 | 后端接口 |
|----------------|----------|
| `getVersionsByKb/getLatestVersion/getPublishedVersion/createVersion` | `/kb/{kbId}/versions*` |
| `transitionStatus` | `POST .../versions/{id}/transition` |
| `getReviewTasks/getPendingReviews/submitForReview/approveReview/rejectReview` | `/reviews*` |
| `addOperationLog/addRetrievalLog/addPublishLog/getKBLogs/getKBLogsByCategory` | `/kb/{kbId}/logs*`（写入内部） |
| `getUpdateLogs/getUpdateLogsByType/getVersionDiff/addUpdateLog` | `/kb/{kbId}/update-logs*` |
