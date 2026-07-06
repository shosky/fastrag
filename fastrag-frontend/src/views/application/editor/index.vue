<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import BasicConfig from './components/BasicConfig.vue'
import KnowledgeConfig from './components/KnowledgeConfig.vue'
import DialogConfig from './components/DialogConfig.vue'
import SkillConfig from './components/SkillConfig.vue'
import ToolConfig from './components/ToolConfig.vue'
import McpConfig from './components/McpConfig.vue'
import VmConfig from './components/VmConfig.vue'
import UiConfig from './components/UiConfig.vue'
import DebugChat from './components/DebugChat.vue'
import MemberConfig from './components/MemberConfig.vue'
import PublishConfig from './components/PublishConfig.vue'
import ChatLogs from './components/ChatLogs.vue'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()
const appId = route.params.id as string
const activeMenu = ref('basic')

// ===========================================================================
// 菜单
// ===========================================================================
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

// 扁平化菜单项
const menuItems = menuGroups.flatMap(group => group.items)

// 展开/折叠分组
function toggleMenuGroup(groupName: string) {
  const group = menuGroups.find(g => g.name === groupName)
  if (group) {
    group.expanded = !group.expanded
  }
}

// 应用信息（从后端加载）
const appInfo = ref({
  id: appId,
  name: '',
  type: '',
  description: '',
  accessToken: '',
})

async function loadAppInfo() {
  try {
    const res: any = await api.getAppDetail(appId)
    if (res) {
      appInfo.value.name = res.name || appInfo.value.name
      appInfo.value.type = res.type || appInfo.value.type
      appInfo.value.description = res.description || appInfo.value.description
      appInfo.value.accessToken = res.accessToken || appInfo.value.accessToken
    }
  } catch {
    // 加载失败使用默认值
  }
}

onMounted(() => {
  loadAppInfo()
})

// ===========================================================================
// 工具函数
// ===========================================================================
function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('复制成功')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

// ===========================================================================
// 底部操作栏
// ===========================================================================
async function handleSave() {
  try {
    await api.saveAppConfig(appId, {})
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handlePublish() {
  try {
    const configSnapshot = await api.getAppConfig(appId)
    await api.publishApp(appId, {
      version: '1.0.0',
      scopeType: 'production',
      configSnapshot,
    })
    ElMessage.success('发布成功')
  } catch {
    ElMessage.error('发布失败')
  }
}
</script>

<template>
  <div class="app-editor">
    <!-- 左侧菜单 -->
    <div class="editor-sidebar">
      <div class="sidebar-header">
        <el-button link @click="router.push('/application')">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <span>{{ appInfo.name }}</span>
      </div>
      <el-scrollbar class="menu-scrollbar">
        <div class="menu-groups">
          <div v-for="group in menuGroups" :key="group.name" class="menu-group">
            <div class="group-header" @click="toggleMenuGroup(group.name)">
              <span class="group-name">{{ group.name }}</span>
              <el-icon class="group-expand-icon">
                <ArrowDown v-if="group.expanded" />
                <ArrowRight v-else />
              </el-icon>
            </div>
            <div v-show="group.expanded" class="group-items">
              <div
                v-for="item in group.items"
                :key="item.key"
                class="menu-item"
                :class="{ active: activeMenu === item.key }"
                @click="activeMenu = item.key"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-scrollbar>
    </div>

    <!-- 右侧内容区 -->
    <div class="editor-content">
      <!-- 配置 -->
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

      <!-- 发布 -->
      <PublishConfig v-if="activeMenu === 'publish'" :app-info="appInfo" />

      <!-- 运营 -->
      <ChatLogs v-if="activeMenu === 'chat-log'" :app-info="appInfo" />

      <!-- 底部操作栏 -->
      <div class="editor-footer">
        <el-button @click="handleSave">保存</el-button>
        <el-button type="primary" @click="handlePublish">发布配置</el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.app-editor {
  display: flex;
  height: 100%;
}

.editor-sidebar {
  width: 200px;
  background: $bg-white;
  border-right: 1px solid $border-lighter;
  display: flex;
  flex-direction: column;

  .sidebar-header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-base;
    border-bottom: 1px solid $border-lighter;
    font-weight: 600;
  }

  .menu-scrollbar {
    flex: 1;
  }
}

.menu-groups {
  padding: $spacing-sm 0;
}

.menu-group {
  margin-bottom: $spacing-xs;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm $spacing-base;
  cursor: pointer;
  user-select: none;

  &:hover {
    background: $bg-hover;
  }

  .group-name {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
  }

  .group-expand-icon {
    font-size: 12px;
    color: $text-secondary;
    transition: transform 0.3s;
  }
}

.group-items {
  .menu-item {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    padding-left: $spacing-xl;
    font-size: 13px;
    color: $text-regular;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: $bg-hover;
      color: $text-primary;
    }

    &.active {
      background: $bg-active;
      color: $color-primary;
      font-weight: 500;
    }

    .el-icon {
      font-size: 16px;
    }
  }
}

.editor-content {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg;
}

.editor-footer {
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
  border-top: 1px solid $border-lighter;
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
}
</style>
