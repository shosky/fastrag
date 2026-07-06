# 虚拟机配置 — 设计文档

## 1. 功能概述

**目的：** 配置应用运行时的虚拟沙箱环境，使智能体能在隔离环境中执行代码和运行服务。

**当前状态：** 编辑器内联 mock 实现（完整表单，无保存/API）。后端无虚拟机相关 API。

**改造目标：** 新建 `VmConfig.vue` 组件，纯前端配置管理。虚拟机的状态存储于 `AppConfig.advancedOptions` 或新建 `app_vm_config` 表。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/apps/{appId}/basic` | 获取基础配置（读取 `advancedOptions`） | ✅ 已实现 |
| PUT | `/api/apps/{appId}/basic/advanced` | 保存高级选项（存 `advancedOptions`） | ✅ 已实现 |

VM 配置作为 `advancedOptions` 中的嵌套 JSON 存储。

### 2.2 数据格式

```json
// advancedOptions 中的 vm 字段
{
  "vm": {
    "enabled": false,
    "image": "ubuntu-22.04",
    "cpu": 2,
    "memory": 4,
    "disk": 20,
    "networkEnabled": true,
    "autoShutdown": true,
    "shutdownTimeout": 30,
    "preInstalledPackages": ["python3", "nodejs", "git"],
    "environmentVars": [
      { "key": "ENV", "value": "production" }
    ]
  }
}
```

### 2.3 本地状态定义

```typescript
const vmConfig = ref({
  enabled: false,
  image: 'ubuntu-22.04',
  cpu: 2,
  memory: 4,
  disk: 20,
  networkEnabled: true,
  autoShutdown: true,
  shutdownTimeout: 30,
  preInstalledPackages: ['python3', 'nodejs', 'git'],
  environmentVars: [{ key: '', value: '' }],
})

const vmImages = [
  { label: 'Ubuntu 22.04 LTS', value: 'ubuntu-22.04' },
  { label: 'Ubuntu 20.04 LTS', value: 'ubuntu-20.04' },
  { label: 'CentOS 7', value: 'centos-7' },
  { label: 'Debian 11', value: 'debian-11' },
  { label: 'Windows Server 2022', value: 'windows-2022' },
]

const newPackage = ref('')
const loading = ref(false)
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
onMounted(() => { loadVmConfig() })
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadVmConfig()` | 从 `advancedOptions` 读取 VM 配置 | `getAppBasicConfig` |
| `handleSave()` | 保存 VM 配置到 `advancedOptions` | `saveAppAdvanced` |
| `addEnvVar()` | 添加环境变量行 | 前端操作 |
| `removeEnvVar(index)` | 删除环境变量行 | 前端操作 |

---

## 4. UI 布局

```
┌─ 虚拟机配置 ──────────────────────────────────────┐
│  ┌────────────────────────────────────────────────┐│
│  │ 启用虚拟机                  [开关]             ││
│  │                                                ││
│  │ (开关开启后显示以下配置)                        ││
│  │ ──────────────────────────────────────────     ││
│  │ 镜像系统:   [Ubuntu 22.04 LTS          ▾]     ││
│  │ CPU (核):   [2          ▾]                     ││
│  │ 内存 (GB):  [4          ▾]                     ││
│  │ 磁盘 (GB):  [20         ▾]                     ││
│  │ 网络访问:   [开关] 允许虚拟机访问外部网络       ││
│  │ 自动关机:   [开关]                             ││
│  │ 超时时间:   [30   ▾] 分钟                      ││
│  │                                                ││
│  │ ── 预装软件包 ───────────────────────────────  ││
│  │ [python3] [nodejs] [git] [___] [+添加]         ││
│  │                                                ││
│  │ ── 环境变量 ─────────────────────────────────  ││
│  │ ┌──────────┬──────────┬──────┐                 ││
│  │ │ 变量名   │ 变量值   │ 操作 │                 ││
│  │ │ ENV      │ production│[×]  │                 ││
│  │ │ LOG_LEVEL│ info     │[×]  │                 ││
│  │ └──────────┴──────────┴──────┘                 ││
│  │ [+ 添加环境变量]                                ││
│  └────────────────────────────────────────────────┘│
│                                                    │
│                                    [保 存]          │
└────────────────────────────────────────────────────┘
```

---

## 5. 实现要点

### 5.1 存储策略

VM 配置没有独立的数据表，作为 `basic/advanced` 中的 `advancedOptions` JSON 字段存储：

```typescript
async function handleSave() {
  await api.saveAppAdvanced(appId(), {
    vm: vmConfig.value
  })
  ElMessage.success('虚拟机配置已保存')
}
```

### 5.2 边界条件
- 虚拟机未启用 → 收起所有配置项，只显示开关
- 自动关机未开启 → 隐藏超时时间选项
- 预装包列表为空 → 显示空标签区域
- 环境变量 key/value 为空 → 保存时过滤掉空行

### 5.3 错误处理
- 加载 `advancedOptions` 中无 vm 字段 → 使用默认值
- 保存失败 → `ElMessage.error('保存失败')`

### 5.4 备注
- 此为纯前端配置管理，实际虚拟机调度由后端服务处理
- VM 镜像列表可后续从 API 动态加载
- 环境变量使用表格编辑，支持动态增删行
