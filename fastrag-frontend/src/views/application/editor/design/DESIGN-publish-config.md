# 分享发布 — 设计文档

## 1. 功能概述

**目的：** 将应用发布到生产环境，并提供分享链接、嵌入代码等分发能力。

**当前状态：** 编辑器内联 mock 实现（完整表单，无真实 API 调用）。

**改造目标：** 新建 `PublishConfig.vue` 组件，对接发布 API。包含三大功能模块：分享链接、嵌入代码、发布管理。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/apps/{appId}/publish/records` | 获取发布记录 | ✅ 已实现 |
| POST | `/api/apps/{appId}/publish/online` | 发布上线（创建记录） | ✅ 已实现 |
| GET | `/api/apps/{appId}/config` | 获取 AppConfig（含当前配置快照） | ✅ 已实现 |

### 2.2 请求/响应格式

**POST /api/apps/{id}/publish/online 请求体：**
```json
{
  "version": "1.0.0",
  "scopeType": "production",
  "configSnapshot": { ... }  // 当前全量配置快照
}
```

**GET /api/apps/{id}/publish/records 响应：**
```json
[
  {
    "id": "pub_001",
    "version": "1.0.0",
    "status": "released",
    "scopeType": "production",
    "operator": "张三",
    "publishedAt": "2026-06-27 14:32:10",
    "createdAt": "2026-06-27 14:30:00"
  }
]
```

### 2.3 本地状态定义

```typescript
// 发布管理
const publishRecords = ref<PublishRecord[]>([])
const activePublishTab = ref('share')  // share | embed | release

// 分享配置
const publishConfig = ref({
  shareEnabled: false,
  accessPassword: '',
  expiresIn: 'never',
})

// 嵌入选项
const embedOptions = ref({
  width: 380,
  height: 560,
  themeColor: '#0167e5',
})

// 发布状态
const publishing = ref(false)
const latestVersion = ref('1.0.0')
```

---

## 3. 组件设计

### 3.1 Props

```typescript
defineProps<{ appInfo: { id: string } }>()
```

### 3.2 Emits

无

### 3.3 生命周期

```typescript
onMounted(() => { loadPublishRecords() })
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadPublishRecords()` | 加载发布记录 | `getAppPublishRecords` |
| `handlePublish()` | 执行发布操作 | `publishApp` |
| `handleRevoke(row)` | 撤回指定版本 | `publishApp`（后端无独立撤回接口） |
| `copyToClipboard(text)` | 复制分享链接/嵌入代码 | 前端 API |

---

## 4. UI 布局

### 4.1 分享链接（Tab）

```
┌─ 分享 & 发布 ──────────────────────────────────────┐
│  [分享链接] [嵌入代码] [发布管理]                    │
│                                                    │
│  ┌────────────────────────────────────────────────┐│
│  │ 分享状态: [开关] 已开启                         ││
│  │                                                ││
│  │ 分享链接:  [https://app.ais.com/share/...] [复制]││
│  │ 任何人拥有此链接即可访问应用                   ││
│  │                                                ││
│  │ 访问密码: [_________] (可选)                    ││
│  │ 有效期:   [永久有效 ▾]                          ││
│  │                                                ││
│  │                    [保存分享设置]               ││
│  └────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────┘
```

### 4.2 嵌入代码（Tab）

```
┌─ 嵌入代码 ─────────────────────────────────────────┐
│  HTML 嵌入代码                                     │
│  ┌────────────────────────────────────────────────┐│
│  │ <iframe src="https://app.ais.com/share/..."   ││
│  │   width="380" height="560"                    ││
│  │   style="border:none;border-radius:8px"       ││
│  │   allow="clipboard-write" />                  ││
│  └────────────────────────────────────────────────┘│
│  [复制代码]                                         │
│                                                    │
│  嵌入选项：                                         │
│  宽: [380] px  高: [560] px  主题色: [■ 选择器]     │
└────────────────────────────────────────────────────┘
```

### 4.3 发布管理（Tab）

```
┌─ 发布管理 ─────────────────────────────────────────┐
│  ┌────┬──────┬────────┬────────┬────────┬────────┐ │
│  │ 版本│ 环境 │ 状态    │ 操作者  │ 发布时间│ 操作   │ │
│  ├────┼──────┼────────┼────────┼────────┼────────┤ │
│  │1.0.0│生产  │已发布  │ 张三   │06-27   │[撤回]  │ │
│  │0.9.0│测试  │已回滚  │ 李四   │06-25   │—       │ │
│  └────┴──────┴────────┴────────┴────────┴────────┘ │
│                                                    │
│                           [发布新版本]              │
└────────────────────────────────────────────────────┘
```

---

## 5. 实现要点

### 5.1 发布流程

```typescript
async function handlePublish() {
  publishing.value = true
  try {
    // 1. 获取当前配置快照
    const config = await api.getAppConfig(appId())

    // 2. 创建发布记录
    await api.publishApp(appId(), {
      version: latestVersion.value,
      scopeType: 'production',
      configSnapshot: config,
    })

    ElMessage.success('发布成功！')
    await loadPublishRecords()
  } catch {
    ElMessage.error('发布失败')
  } finally {
    publishing.value = false
  }
}
```

### 5.2 分享链接 Token

应用分享链接使用应用 ID 或专属分享 Token 生成：
```
分享 URL = https://app.ais.com/share/{appId}[?pwd={password}]
```

### 5.3 边界条件
- 分享未开启 → 不显示链接和密码字段
- 未设置密码 → 链接不追加 `?pwd=` 参数
- 发布记录为空 → 显示空状态"暂无发布记录"
- 版本号自动递增 → 根据已有记录自动计算 `v{major}.{minor}.{patch}`

### 5.4 错误处理
- 发布失败 → 保留表单状态，提示重试
- 撤回失败 → `ElMessage.error('撤回失败')`
- 复制到剪贴板失败 → `ElMessage.error('复制失败')`

### 5.5 备注
- 嵌入代码随 `embedOptions` 变化实时更新
- 发布管理仅显示版本记录，不包含实际部署管道
- 分享配置（密码、有效期）目前为前端展示，后端 `AppPublishRecord` 实体的 `configSnapshot` 可用于存储此类元数据
