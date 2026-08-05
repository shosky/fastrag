# FastRAG 运营中心模块实现文档

## 一、概述

本文档覆盖两个运营中心模块的完整实现方案：
1. **Feedback 模块**（`/operation/feedback`）— 完成度约 65%，需收尾
2. **Model Monitor 模块**（`/operation/model-monitor`）— 完成度约 20%，需从零建设后端

遵循项目现有架构模式：
- **后端**：Controller + Service + MyBatis-Plus Mapper，跨模块通过 Maven 依赖 + 直接注入 Mapper
- **前端**：Vue 3 + TypeScript + Element Plus，API 层统一在 `src/api/index.ts`，分页用 `usePagination` composable

---

## 二、Feedback 模块实现（剩余 35%）

### 2.1 后端新增：Feedback 总览统计 API

#### 修改文件
**`FeedbackController.java`** — 新增 `GET /api/feedback/overview`

```
GET /api/feedback/overview?kbId=xxx
```

#### 响应结构

```json
{
  "metrics": {
    "totalQaCount": 12345,           // 累计问答量 → chat_session 表 COUNT
    "totalFeedbackCount": 3456,       // 累计反馈量 → user_feedback COUNT
    "satisfactionRate": 85.6,         // 反馈满意度 → like占比
    "feedbackRate": 28.0,             // 反馈率 → feedbackCount / qaCount * 100
    "unresolvedRate": 12.3            // 未解决问题占比 → (pending+dislike)占比
  },
  "questionCategories": [             // 问题分类分析
    { "name": "产品功能", "count": 1230, "percentage": 35 },
    { "name": "技术支持", "count": 890, "percentage": 25 }
    // 从 feedback.category 字段 GROUP BY，无数据时返回空数组
  },
  "hotKeywords": [                    // 高频词云
    { "word": "登录", "count": 234 },
    { "word": "知识库", "count": 189 }
    // 从 feedback.query / chat_session.query 分词统计
  ],
  "appSatisfactionRanking": [         // 应用满意度排行
    { "rank": 1, "appId": "app_xxx", "name": "智能问答助手", "satisfaction": 92.3, "feedbackCount": 1230 }
    // GROUP BY app_id，JOIN app 表取名称
  ]
}
```

#### 实现要点
- `FeedbackService` 接口新增 `getOverview(kbId)` 方法
- `FeedbackServiceImpl` 注入 `ChatSessionMapper`（已存在）用于统计问答量
- 注入 `AppMapper`（依赖 `fastrag-application` 已有）用于应用满意度排行的名称
- 按 **问题分类（category）**：`SELECT category, COUNT(*) FROM user_feedback GROUP BY category`
- 词云：从 `feedback.query` 提取高频词（按空格/标点拆分后 COUNT），取 Top 20
- AI 优化建议：纯前端规则（见 2.4）

### 2.2 前端新增：API 层

**`src/api/index.ts`** 新增：

```typescript
export async function getFeedbackOverview(kbId?: string) {
  return request.get('/feedback/overview', { params: { kbId } })
}
```

### 2.3 前端修改：feedback.vue — 总览 Tab 接入真实数据

#### 数据加载

```typescript
// 替换行 6-12 的硬编码 metrics
const overviewData = ref<any>({})
const overviewLoading = ref(false)

async function loadOverview() {
  overviewLoading.value = true
  try {
    const res: any = await api.getFeedbackOverview(feedbackSearch.value.kbId || undefined)
    overviewData.value = res || {}
  } finally {
    overviewLoading.value = false
  }
}
```

- metrics 卡片绑定 `overviewData.value.metrics.*`
- 问题分类绑定 `overviewData.value.questionCategories`
- 词云绑定 `overviewData.value.hotKeywords`
- 满意度排行绑定 `overviewData.value.appSatisfactionRanking`
- `loadFeedbackStats()` 中同时调用 `loadOverview()`

### 2.4 前端实现：AI 优化建议

替换 `handleGenerateAISuggestion()` 为规则引擎：

```typescript
function handleGenerateAISuggestion() {
  const m = overviewData.value?.metrics
  if (!m) { ElMessage.info('请先加载数据'); return }
  const suggestions: string[] = []
  if (m.satisfactionRate < 80) suggestions.push('反馈满意度偏低（<80%），建议检查回答质量')
  if (m.feedbackRate < 20) suggestions.push('反馈率偏低（<20%），建议引导用户反馈')
  if (m.unresolvedRate > 20) suggestions.push('未解决问题占比偏高（>20%），建议优化常见问题的知识覆盖')
  if (!suggestions.length) suggestions.push('当前运营状态良好，暂无优化建议')
  ElMessageBox.alert(suggestions.map(s => `• ${s}`).join('<br>'), 'AI 优化建议', {dangerouslyUseHTMLString: true})
}
```

### 2.5 前端修改：问答明细 Tab 接入真实数据

#### 数据源
- 从 `chat_session` 表获取问答记录
- 新增 API：`GET /api/chat-sessions?page=&pageSize=&question=&user=`
- 或者直接复用已有的 HomeController 能获取到的数据

**建议方案：** 新增 `ChatSessionController` 或扩展 `AnalyticsController`：

```
GET /api/chat-sessions?page=1&pageSize=10&keyword=&userId=
```

**`src/api/index.ts`** 新增：

```typescript
export async function getChatSessions(params?: { 
  keyword?: string; userId?: string; page?: number; pageSize?: number 
}) {
  return request.get('/chat-sessions', { params })
}
```

**`feedback.vue`** 修改：
- 替换 `qaDetails` 硬编码（行 47-51）为 `qaDetails` + `qaTotal` + `qaLoading`
- 加载函数使用 `api.getChatSessions(...)`
- "查询" 和 "重置" 按钮绑定 `handleQaSearch()` / `handleQaReset()`
- 表格底部增加分页组件（按 AGENTS.md 规范）

### 2.6 前端修改：反馈明细分页 — 按 AGENTS.md 规范整改

**当前问题**（行 294-303）：
- 使用旧式 `:current-page`/`:page-size` props
- layout 缺少 `sizes` 和 `jumper`
- 未使用 `usePagination` composable

**整改后模板**：

```vue
<div class="feedback__pagination">
  <el-pagination
    v-model:current-page="currentPage"
    v-model:page-size="pageSize"
    :total="feedbackTotal"
    :page-sizes="[10, 20, 50, 100]"
    layout="total, sizes, prev, pager, next, jumper"
    @current-change="handleCurrentChange"
    @size-change="handleSizeChange"
  />
</div>
```

**整改后 script**：

```typescript
import { usePagination } from '@/composables/usePagination'

const { 
  currentPage, pageSize, total, 
  handleCurrentChange, handleSizeChange 
} = usePagination(10)

// 移除 feedbackSearch.page/pageSize 的手动管理
// 在 loadFeedbacks 中使用 currentPage.value / pageSize.value
// 在 handleCurrentChange 中调用 loadFeedbacks
// 在 handleSizeChange 中重置 currentPage=1 后调用 loadFeedbacks
```

**样式**（遵循 BEM + 统一规范）：

```scss
.feedback__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
```

### 2.7 后端配合：ChatSession 分页查询

在 `ChatSessionMapper` 基础上，`ChatSessionController` 或 `AnalyticsController` 新增：

```
GET /api/chat-sessions?keyword=&userId=&page=1&pageSize=10
```

返回分页数据：`{ list: [...], total: N }`

---

## 三、Model-Monitor 模块实现（80% 新建）

### 3.1 后端：新增 Maven 依赖

**`fastrag-operation/pom.xml`** 添加：

```xml
<dependency>
  <groupId>com.fastrag</groupId>
  <artifactId>fastrag-platform</artifactId>
</dependency>
```

### 3.2 后端：模型（ModelCallLog）查询封装

**新建 `ModelCallLogMapper`** — 在 operation 模块创建：
（注意：platform 模块已有 `ModelCallLogMapper`，operation 模块可以直接 import 使用）

**方案说明**：operation 模块添加 platform 依赖后，直接 import `com.fastrag.module.platform.mapper.ModelCallLogMapper` 并注入。

### 3.3 后端：ModelMonitorController

**新建 `fastrag-operation/.../controller/ModelMonitorController.java`**

```
@RestController
@RequestMapping("/api/monitor/model")
```

| 端点 | 说明 | 参数 |
|------|------|------|
| `GET /api/monitor/model/summary` | 4 个指标卡片 | `timeRange` (7/30/180) |
| `GET /api/monitor/model/distribution` | 模型使用分布 | `timeRange` |
| `GET /api/monitor/model/top-apps` | 高消耗应用排行 | `timeRange`, `limit` (default 5) |
| `GET /api/monitor/model/stats` | 模型调用统计表 | `timeRange`, `keyword`, `page`, `pageSize` |

或合并为一个聚合端点：

```
GET /api/monitor/model/overview?timeRange=7&keyword=&page=1&pageSize=10
```

返回统一结构包含所有数据。

#### 数据聚合逻辑（使用 MyBatis-Plus + 原生 SQL 聚合）

**4 个指标：**

```java
// 总 Token
mapper.selectObjs(new QueryWrapper<ModelCallLog>()
    .select("COALESCE(SUM(tokens), 0)")
    .apply("timestamp >= DATE_SUB(NOW(), INTERVAL {0} DAY)", timeRange)
);

// API 调用次数
mapper.selectCount(wrapper);

// 平均响应时间  
mapper.selectObjs(new QueryWrapper<ModelCallLog>()
    .select("COALESCE(AVG(duration), 0)")
    .apply("timestamp >= DATE_SUB(NOW(), INTERVAL {0} DAY)", timeRange)
);

// 总失败次数
mapper.selectCount(wrapper.eq(ModelCallLog::getStatus, "failed"));
```

**模型使用分布：** `GROUP BY model_id`
```java
mapper.selectMaps(new QueryWrapper<ModelCallLog>()
    .select("model_id, COUNT(*) as calls, SUM(tokens) as tokens")
    .apply("timestamp >= DATE_SUB(NOW(), INTERVAL {0} DAY)", timeRange)
    .groupBy("model_id")
    .orderByDesc("tokens")
);
```

**高消耗应用排行：** `GROUP BY caller`
```java
mapper.selectMaps(new QueryWrapper<ModelCallLog>()
    .select("caller, SUM(tokens) as tokens, COUNT(*) as calls")
    .apply("timestamp >= DATE_SUB(NOW(), INTERVAL {0} DAY)", timeRange)
    .groupBy("caller")
    .orderByDesc("tokens")
    .last("LIMIT " + limit)
);
```

**注意：** `caller` 字段存储的是调用方标识（如 appId），可以在前端直接展示，或在后端 Join app 表取名称。

### 3.4 后端：数据模型定义

**新建 `ModelMonitorData.java`** — 在 `fastrag-operation/src/main/java/com/fastrag/module/operation/model/`：

```java
@Data @Builder
public class ModelMonitorData {
    private List<MetricItem> metrics;
    private List<ModelDistItem> distribution;
    private List<TopAppItem> topApps;
    private PageResult<ModelStatsItem> stats;
    
    @Data @Builder
    public static class MetricItem {
        private String label;
        private String value;     // "1,234,567"
        private String change;    // "+12.3%"
        private String trend;     // "up" / "down"
    }
    
    @Data @Builder
    public static class ModelDistItem {
        private String name;       // 模型名称
        private double percentage; // 45
        private String token;      // "556,780"
    }
    
    @Data @Builder
    public static class TopAppItem {
        private int rank;
        private String name;
        private String token;
        private String cost;       // "￥2,283.90"（按每千 token 单价计算，或暂不计算）
    }
    
    @Data @Builder
    public static class ModelStatsItem {
        private String code;       // 模型 code
        private long calls;        // 调用总量
        private long fails;        // 失败量
        private String token;      // Token消耗
        private String cost;       // 消耗金额
    }
}
```

### 3.5 前端新增：API 层

**`src/api/index.ts`** 新增：

```typescript
// ===========================================================================
// 模型监控 API
// ===========================================================================

export async function getModelMonitorOverview(params?: { 
  timeRange?: number; keyword?: string; page?: number; pageSize?: number 
}) {
  return request.get('/monitor/model/overview', { params })
}
```

### 3.6 前端修改：model-monitor.vue — 全部替换 Mock 数据

**核心变更：**

| 当前（Mock） | 修改后（真实 API） |
|---|---|
| `metrics` ref 硬编码 | `const metrics = computed(() => overviewData.value?.metrics || [])` |
| `modelUsage` ref 硬编码 | `const modelUsage = computed(() => overviewData.value?.distribution || [])` |
| `highConsumeApps` ref 硬编码 | `const highConsumeApps = computed(() => overviewData.value?.topApps || [])` |
| `modelStats` ref 硬编码 | `const modelStats = computed(() => overviewData.value?.stats?.list || [])` |
| `searchModel` 前端过滤 | 后端分页 + keyword 过滤 |
| `timeRange` 无绑定 | 绑定到 API 请求参数 |

**数据加载脚本：**

```typescript
import { ref, computed, onMounted, watch } from 'vue'
import * as api from '@/api'
import { usePagination } from '@/composables/usePagination'

const timeRange = ref(7)  // 改为 number 类型，便于 API 使用
const overviewData = ref<any>({})
const overviewLoading = ref(false)
const searchModel = ref('')

const { currentPage, pageSize, total, handleCurrentChange, handleSizeChange } = usePagination(10)

async function loadOverview() {
  overviewLoading.value = true
  try {
    const res: any = await api.getModelMonitorOverview({
      timeRange: timeRange.value,
      keyword: searchModel.value || undefined,
      page: currentPage.value,
      pageSize: pageSize.value,
    })
    overviewData.value = res || {}
    total.value = res?.stats?.total || 0
  } finally {
    overviewLoading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadOverview()
}

function handleReset() {
  searchModel.value = ''
  currentPage.value = 1
  loadOverview()
}

watch(timeRange, () => loadOverview())

onMounted(loadOverview)
```

**分页组件**（按 AGENTS.md 规范）：

```vue
<div class="model-monitor__pagination">
  <el-pagination
    v-model:current-page="currentPage"
    v-model:page-size="pageSize"
    :total="total"
    :page-sizes="[10, 20, 50, 100]"
    layout="total, sizes, prev, pager, next, jumper"
    @current-change="handleCurrentChange"
    @size-change="handleSizeChange"
  />
</div>
```

**时间范围选择器**改为绑定数值：

```vue
<el-select v-model="timeRange" size="small" style="width: 120px">
  <el-option :value="7" label="近7天" />
  <el-option :value="30" label="近30天" />
  <el-option :value="180" label="近6个月" />
</el-select>
```

**消耗金额计算**：若后端暂不计算金额，前端可显示 "—"，或使用简易计算公式。

---

## 四、文件变更清单

### Feedback 模块

| 操作 | 文件路径 | 说明 |
|------|----------|------|
| 🖊 修改 | `fastrag-operation/.../service/FeedbackService.java` | 新增 `getOverview()` 方法 |
| 🖊 修改 | `fastrag-operation/.../service/impl/FeedbackServiceImpl.java` | 实现 overview 聚合查询 |
| 🖊 修改 | `fastrag-operation/.../controller/FeedbackController.java` | 新增 `GET /overview` 端点 |
| 🖊 修改 | `src/api/index.ts` | 新增 `getFeedbackOverview()` |
| 🖊 修改 | `src/views/operation/feedback.vue` | 总览数据接入、问答明细接入、分页整改、AI 建议实现 |
| 🆕 新建 | `fastrag-operation/.../controller/ChatSessionController.java` | 问答明细分页接口 |
| 🖊 修改 | `fastrag-operation/.../service/FeedbackService.java` | 无需修改（ChatSessionMapper 已存在） |

### Model-Monitor 模块

| 操作 | 文件路径 | 说明 |
|------|----------|------|
| 🖊 修改 | `fastrag-operation/pom.xml` | 添加 `fastrag-platform` 依赖 |
| 🆕 新建 | `fastrag-operation/.../model/ModelMonitorData.java` | 响应数据模型 |
| 🆕 新建 | `fastrag-operation/.../service/ModelMonitorService.java` | 服务接口 |
| 🆕 新建 | `fastrag-operation/.../service/impl/ModelMonitorServiceImpl.java` | 实现聚合查询 |
| 🆕 新建 | `fastrag-operation/.../controller/ModelMonitorController.java` | REST 控制器 |
| 🖊 修改 | `src/api/index.ts` | 新增 `getModelMonitorOverview()` |
| 🖊 修改 | `src/views/operation/model-monitor.vue` | 全部数据替换为真实 API，分页规范整改 |

---

## 五、实施顺序建议

### 第一阶段：Feedback 总览后端（0.5 天）
1. `FeedbackService` + `FeedbackServiceImpl.getOverview()` — 聚合查询
2. `FeedbackController.getOverview()` — 新增端点
3. ChatSession 分页查询端点

### 第二阶段：Feedback 总览前端（0.5 天）
1. API 层：新增 `getFeedbackOverview()`
2. `feedback.vue` 总览 Tab：指标卡片 + 分类分析 + 词云 + 满意度排行 数据绑定
3. AI 优化建议实现
4. 问答明细 Tab 接入真实数据

### 第三阶段：Feedback 分页规范整改（0.25 天）
1. `usePagination` composable 引入
2. 分页组件模板整改
3. SCSS 样式统一

### 第四阶段：Model-Monitor 后端（1 天）
1. `pom.xml` 添加 platform 依赖
2. `ModelMonitorData.java` 数据模型
3. `ModelMonitorServiceImpl` — 四个聚合查询
4. `ModelMonitorController` — REST 端点

### 第五阶段：Model-Monitor 前端（0.5 天）
1. API 层：新增 `getModelMonitorOverview()`
2. `model-monitor.vue` 全部数据替换
3. 时间范围绑定、搜索/重置、分页规范

**总计预估工时：约 3 个工作日**

---

## 六、注意事项

1. **数据库字段冗余**：`model_call_log` 表有两个 `status` 字段，需确认实际生效字段为第二个（`DEFAULT 'success'`）。建议后续清理 DDL。
2. **消耗金额计算**：当前 `model_call_log` 表无单价字段，无法精确计算成本。前端可暂显示 "—"，或按固定估算单价（如 ￥5/百万 token）粗略计算。
3. **词云实现精度**：中文分词在 SQL 层面较困难，可用简单方案：按空格/标点拆分后统计词频，取 Top 20。如需更精确分词，考虑引入 Elasticsearch 或 Java 分词库。
4. **无数据降级**：所有 API 在无数据时应返回空结构而非 null，前端用 `?.` 安全访问 + `|| []` / `|| {}` 兜底。
5. **分页一致性**：两个 module 的分页组件最终形态一致，均遵循 `AGENTS.md` 规范。
