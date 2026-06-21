# 模块九：运营与审计

> 对应前端：`src/views/operation/*`（运营中心）、`src/views/admin/audit/*`（安全审计）
> 数据契约：聚合各模块日志/指标，`src/views/operation/*` 为只读分析看板

---

## 一、业务概述

系统的「可观测」层：运营指标分析 + 安全审计。均为只读聚合，数据来自其它模块的写入。

### 1.1 运营中心（对齐 `views/operation/*`）

| 看板 | 内容 |
|------|------|
| 知识资产分析（`kb-analytics`） | KB 数量/分类分布、文件数、分片数、存储用量、增长趋势 |
| 模型监控（`model-monitor`） | 模型调用量、Token 消耗、平均耗时、成功率、成本 |
| 问答反馈（`feedback`） | 用户问答反馈聚合（点赞/点踩/无反馈）、低分问答 |
| 问答明细（`qa-detail`） | 单条问答会话追溯（query→召回→答案→反馈） |

### 1.2 安全审计（对齐 `views/admin/audit/*`）

| 页面 | 内容 |
|------|------|
| 系统日志（`system-log`） | 全局操作审计日志（跨所有用户/资源） |
| 设备登录分析（`device-login`） | 登录设备/IP/地理分布、异常登录 |
| 登录安全（`login-security`） | 登录策略（密码强度/锁定/双因素/会话超时） |
| 审核中心（`review-center`） | 内容/发布审核队列（与模块5审核任务联动） |

---

## 二、数据模型（Entity）

本模块以聚合查询为主，少量独立表：

### 2.1 `user_feedback` 问答反馈

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| session_id | varchar(32) | 问答会话 |
| user_id | varchar(32) | |
| kb_id / app_id | varchar(32) | |
| query | text | |
| answer | text | |
| feedback | enum('like','dislike','none') | |
| score | int | 1-5 |
| comment | text | |
| created_at | datetime | |

### 2.2 `chat_session` 问答会话

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| user_id / kb_id / app_id | varchar(32) | |
| query / answer | text | |
| retrieved_chunks | json | 召回片段（溯源） |
| model | varchar(64) | |
| duration | bigint | ms |
| tokens | int | |
| created_at | datetime | |

### 2.3 `sys_login_log` 登录日志（对齐 `device-login`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| user_id / username | varchar | |
| login_time | datetime | |
| ip | varchar | |
| device / os / browser | varchar | |
| location | varchar | IP 解析地理 |
| status | enum('success','failed') | |
| fail_reason | varchar | |

### 2.4 `sys_audit_log` 系统审计日志

跨资源全局操作日志（区别于模块5的 KB 级日志，此处是平台级）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| user_id / username | varchar | |
| module | varchar | 操作模块 |
| action | varchar | 动作 |
| target | varchar | 对象 |
| detail | text | |
| ip | varchar | |
| timestamp | datetime | |

### 2.5 `login_security_config` 登录安全策略

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| min_password_length | int | |
| require_special_char | tinyint | |
| max_fail_attempts | int | 锁定阈值 |
| lock_duration | int | 锁定分钟 |
| session_timeout | int | 会话超时分钟 |
| enable_2fa | tinyint | 双因素 |

---

## 三、接口设计（API）

### 3.1 运营分析（`/operation/*`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/analytics/kb` | 知识资产分析（KB数/文件/分片/存储/趋势） | 已认证 |
| GET | `/analytics/kb/trend?days=30` | KB 增长趋势 | 已认证 |
| GET | `/analytics/model` | 模型监控（调用量/Token/耗时/成本） | 已认证 |
| GET | `/analytics/model/{id}/calls?from=&to=` | 单模型调用明细 | 已认证 |
| GET | `/analytics/feedback` | 问答反馈聚合（`?kbId=&from=&to=`） | 已认证 |
| GET | `/analytics/feedback/low-score` | 低分问答列表 | 已认证 |
| GET | `/analytics/sessions/{id}` | 问答会话明细（溯源） | 已认证 |

### 3.2 安全审计（`/admin/audit/*`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/audit/system-log` | 系统审计日志（分页/过滤） | super_admin |
| GET | `/audit/login-log` | 登录日志（`?userId=&status=`） | super_admin |
| GET | `/audit/login-log/devices` | 设备登录分析（聚合） | super_admin |
| GET/PUT | `/audit/login-security` | 登录安全策略查询/更新 | super_admin |

### 3.3 反馈（用户侧）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/feedback` | 提交问答反馈 `{ sessionId, feedback, score?, comment? }` |

---

## 四、核心逻辑设计

### 4.1 运营指标聚合

指标数据来源：

| 指标 | 来源 |
|------|------|
| KB 数/分类分布 | `kb` 表 GROUP BY category |
| 文件/分片/存储 | `kb_file` / `kb_chunk` 聚合 |
| 模型调用量/Token/耗时 | `model_call_log`（模块8） |
| 问答量/反馈 | `chat_session` / `user_feedback` |

```java
@Service
public class AnalyticsService {
    public KbAnalytics kbAnalytics() {
        return KbAnalytics.builder()
            .kbCount(kbMapper.count())
            .categoryStats(kbMapper.countByCategory())      // GROUP BY category
            .fileCount(fileMapper.sumCount())
            .chunkCount(chunkMapper.sumCount())
            .totalSize(fileMapper.sumSize())
            .trend(kbMapper.dailyGrowth(30))                // 近30天每日新增
            .build();
    }
}
```

### 4.2 趋势计算

按天聚合（`GROUP BY DATE(created_at)`），返回 `[{date, count}]` 时间序列，前端绘趋势图。大数据量预聚合到 `daily_stats` 表（定时任务每日汇总）。

### 4.3 系统审计日志切面

通过 AOP 切面自动记录写操作：

```java
@Aspect @Component
public class AuditLogAspect {
    @AfterReturning("@annotation(auditable)")
    public void record(JoinPoint jp, Auditable auditable) {
        SysAuditLog log = SysAuditLog.of(
            SecurityUtil.currentUserId(),
            auditable.module(),
            auditable.action(),
            jp.getArgs(),
            RequestUtil.clientIp()
        );
        auditLogMapper.insert(log);  // 异步落库（MQ）
    }
}
```

注解 `@Auditable(module="kb", action="create")` 标注在各写接口上。

### 4.4 登录安全策略执行

- 登录失败计数（Redis），达 `max_fail_attempts` → 锁定账户 `lock_duration` 分钟。
- 密码强度校验（`min_password_length` + `require_special_char`）。
- 会话超时：JWT TTL + Redis 续期，超时自动失效。
- 双因素：`enable_2fa` 开启后登录需第二步 OTP 校验。

### 4.5 设备/地理分析

- 解析 `User-Agent` → device/os/browser。
- IP → 地理（GeoIP 库 MaxMind）。
- 异常检测：新设备/异地登录标记，供 `device-login` 高亮。

---

## 五、前端覆盖核对表

| 前端页面 | 后端接口 |
|----------|----------|
| `kb-analytics.vue` | `/analytics/kb[/trend]` |
| `model-monitor.vue` | `/analytics/model[/{id}/calls]` |
| `feedback.vue` | `/analytics/feedback[/low-score]` |
| `qa-detail.vue` | `/analytics/sessions/{id}` |
| `system-log.vue` | `/audit/system-log` |
| `device-login.vue` | `/audit/login-log[/devices]` |
| `login-security.vue` | `/audit/login-security` |
| `review-center.vue` | 复用模块5 `/reviews*` |
| 反馈提交（问答页） | `POST /feedback` |

---

## 六、模块间依赖汇总（全模块）

```
运营审计(9) ──读取──▶ 所有模块的日志/指标
平台配置(8) ──数据──▶ 检索(3)/图谱评估(4)/技能MCP(7)
应用编排(6) ──调用──▶ 检索(3) + AI网关 + 工具技能(7)
发布审核(5) ──日志──▶ 写入方：知识库(2)/检索(3)
图谱评估(4) ──调用──▶ 检索(3) + AI网关
检索(3) ──读取──▶ 知识库分片(2) + 配置(8术语/规则)
知识库(2) ──写入──▶ Milvus/ES/Neo4j + 日志(5)
IAM(1) ──被依赖──▶ 所有模块鉴权
```
