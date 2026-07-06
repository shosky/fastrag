# 编辑器页面重构 — 设计文档

## 1. 功能概述

**目的：** 重构 `editor/index.vue`，将菜单从 22 项精简为 12 项，清理所有死代码和内联 mock 实现。

**核心改动：**
1. 重组 `menuGroups`，删除 10 个不必要的菜单项
2. 删除脚本区所有死代码（内联 mock 数据、无用的表单变量、空的 handle 函数）
3. 删除模板区所有内联 inline 区域（改为使用子组件）
4. 底部"保存"和"发布配置"按钮对接真实 API

---

## 2. 新菜单结构

```typescript
const menuGroups = reactive([
  {
    name: '配置',
    expanded: true,
    items: [
      { key: 'basic', label: '基础配置', icon: 'Setting' },
      { key: 'kb', label: '知识库配置', icon: 'Collection' },
      { key: 'skill', label: '技能', icon: 'MagicStick' },
      { key: 'tool', label: '工具', icon: 'SetUp' },
      { key: 'mcp', label: 'MCP', icon: 'Connection' },
      { key: 'vm', label: '虚拟机', icon: 'Monitor' },
      { key: 'ui', label: '界面', icon: 'Monitor' },
      { key: 'dialog', label: '对话', icon: 'ChatDotRound' },
      { key: 'debug', label: '对话调试', icon: 'ChatDotRound' },
      { key: 'member', label: '成员', icon: 'User' },
    ],
  },
  {
    name: '发布',
    expanded: true,
    items: [
      { key: 'publish', label: '分享发布', icon: 'Share' },
    ],
  },
  {
    name: '运营',
    expanded: true,
    items: [
      { key: 'chat-log', label: '对话记录', icon: 'ChatLineRound' },
    ],
  },
])
```

**删除的 10 个菜单项：**
- 导航配置 (`nav`)、参数配置 (`param`)、Prompt编写 (`prompt`)、全局策略 (`global`)、数据库配置 (`db`)、知识更新 (`kb-update`)、对话测试 (`dialog-test`)、对话优化 (`dialog-optimize`)、发布监控 (`monitor-publish`)、企业集成 (`integration`)、反馈记录 (`feedback`)、应用首页 (`home`)

---

## 3. 模板渲染（组件映射）

```html
<BasicConfig v-if="activeMenu === 'basic'" :app-info="appInfo" />
<KnowledgeConfig v-if="activeMenu === 'kb'" :app-info="appInfo" />
<SkillConfig v-if="activeMenu === 'skill'" :app-info="appInfo" />
<ToolConfig v-if="activeMenu === 'tool'" :app-info="appInfo" />
<McpConfig v-if="activeMenu === 'mcp'" :app-info="appInfo" />
<VmConfig v-if="activeMenu === 'vm'" :app-info="appInfo" />
<UiConfig v-if="activeMenu === 'ui'" :app-info="appInfo" />
<DialogConfig v-if="activeMenu === 'dialog'" :app-info="appInfo" />
<DebugChat v-if="activeMenu === 'debug'" :app-info="appInfo" />
<MemberConfig v-if="activeMenu === 'member'" :app-info="appInfo" />
<PublishConfig v-if="activeMenu === 'publish'" :app-info="appInfo" />
<ChatLogs v-if="activeMenu === 'chat-log'" :app-info="appInfo" />
```

**删除所有内联 v-if 区块：**
- `activeMenu === 'skill'` → 改为 `<SkillConfig>`
- `activeMenu === 'tool'` → 改为 `<ToolConfig>`
- `activeMenu === 'mcp'` → 改为 `<McpConfig>`
- `activeMenu === 'vm'` → 改为 `<VmConfig>`
- `activeMenu === 'ui'` → 改为 `<UiConfig>`
- `activeMenu === 'nav'` → 删除
- `activeMenu === 'param'` → 删除
- `activeMenu === 'prompt'` → 删除
- `activeMenu === 'debug'` → 改为 `<DebugChat>`
- `activeMenu === 'member'` → 改为 `<MemberConfig>`
- `activeMenu === 'integration'` → 删除
- `activeMenu === 'publish'` → 改为 `<PublishConfig>`
- `activeMenu === 'chat-log'` → 改为 `<ChatLogs>`
- `activeMenu === 'feedback'` → 删除
- `activeMenu === 'home'` → 删除

---

## 4. 脚本区清理清单

### 删除的代码块

| 行范围 | 变量/函数 | 原因 |
|--------|-----------|------|
| L21-51 | `llmModels` / `embeddingModels` / `rerankModels` + 加载逻辑 | 模型选择已不在菜单中 |
| L54-60 | `appInfo` 内联赋值 | 应由父级通过 props 传入 |
| L124-146 | `basicForm` / `advancedForm` / `handleSaveBasic()` / `handleSaveKB()` / `copyToClipboard()`/`handleSaveBasic` | 被 `BasicConfig.vue` 替代 |
| L149-203 | `kbSearchKeyword` / `categories` / `availableKBs` / `filteredKBs` / `handleSelectAll()` / `handleSaveKB()` | 被 `KnowledgeConfig.vue` 替代 |
| L206-231 | `availableSkills` / `selectedSkills` / `availableTools` / `selectedTools` / `availableMcp` / `selectedMcp` | 被 `SkillConfig.vue`/`ToolConfig.vue`/`McpConfig.vue` 替代 |
| L233-267 | `vmConfig` / `vmImages` / `addEnvVar()` / `removeEnvVar()` / `newPackage` | 被 `VmConfig.vue` 替代 |
| L268-294 | `uiConfig` / `colorTemplates` | 被 `UiConfig.vue` 替代 |
| L296-354 | `navSearchKeyword` / `navList` / `handleAddNav()` / `handleDeleteNav()` / `handleNavQuery()` | 导航配置已删除 |
| L356-412 | `retrievalParams` / `promptSteps` / `promptContent` / `handleSavePrompt()` / `handleResetPrompt()` / `insertVariable()` | 参数配置/Prompt 已删除 |
| L416-440 | `debugMessages` / `debugInput` / `debugLoading` / `handleDebugSend()` / `handleDebugClear()` | 被 `DebugChat.vue` 替代 |
| L442-509 | `memberSearchKeyword` / `memberList` / `handleEditMember()` / `handleSaveMember()` / `handleDeleteMember()` / `handleAssignRole()` / `handleSaveRole()` | 被 `MemberConfig.vue` 替代 |
| L511-552 | `apiKeys` / `webhookConfig` / `handleCreateApiKey()` / `handleSaveWebhook()` | 企业集成已删除 |
| L554-585 | `publishConfig` / `embedOptions` / `publishChannels` / `handleSavePublish()` / `togglePublishChannel()` | 被 `PublishConfig.vue` 替代 |
| L587-646 | `chatLogs` / `filteredChatLogs` / `handleChatLogQuery()` / `handleChatLogReset()` / `handleViewChatDetail()` / `handleDeleteChatLog()` / `handleExportChatLog()` | 被 `ChatLogs.vue` 替代 |
| L648-730 | `feedbacks` / `filteredFeedbacks` / `feedbackStats` / `handleFeedbackQuery()` / `handleProcessFeedback()` / `handleSubmitFeedbackReply()` / `handleExportFeedback()` | 反馈记录已删除 |
| L732-750 | `previewMode` / `handleOpenApp()` / `handlePublishConfig()` | 应用首页已删除 |

### 保留的代码

- `route` / `router` / `appId` — 路由参数
- `activeMenu` / `menuGroups` — 菜单状态（需重建）
- `toggleMenuGroup()` — 菜单折叠/展开

---

## 5. 底部按钮改造

```typescript
// 保存按钮：调用保存 API
async function handleSave() {
  try {
    await api.saveAppConfig(appId, { /* 当前配置快照 */ })
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  }
}

// 发布按钮：调用发布 API
async function handlePublish() {
  try {
    await api.publishApp(appId, {
      version: generateVersion(),
      scopeType: 'production',
      configSnapshot: await api.getAppConfig(appId),
    })
    ElMessage.success('发布成功')
  } catch {
    ElMessage.error('发布失败')
  }
}
```

---

## 6. 组件导入

```typescript
import BasicConfig from './components/BasicConfig.vue'
import KnowledgeConfig from './components/KnowledgeConfig.vue'
import SkillConfig from './components/SkillConfig.vue'
import ToolConfig from './components/ToolConfig.vue'
import McpConfig from './components/McpConfig.vue'
import VmConfig from './components/VmConfig.vue'
import UiConfig from './components/UiConfig.vue'
import DialogConfig from './components/DialogConfig.vue'
import DebugChat from './components/DebugChat.vue'
import MemberConfig from './components/MemberConfig.vue'
import PublishConfig from './components/PublishConfig.vue'
import ChatLogs from './components/ChatLogs.vue'
```

---

## 7. 样式调整

- 删除所有与被移除菜单项相关的 CSS（UI 配置样式、参数配置、Prompt 配置等）
- 保留 `editor-sidebar` / `editor-content` / `editor-footer` 等公共样式
- 保留 `.menu-groups` / `.menu-item` 等通用样式

---

## 8. 实施顺序

1. **步骤一：** 清理脚本区（删除所有死代码）
2. **步骤二：** 重建 `menuGroups`（12 项新结构）
3. **步骤三：** 替换模板区（组件替换内联区块）
4. **步骤四：** 改造底部按钮
5. **步骤五：** 清理 CSS
6. **步骤六：** 更新组件导入
7. **步骤七：** 验证菜单切换和功能完整性
