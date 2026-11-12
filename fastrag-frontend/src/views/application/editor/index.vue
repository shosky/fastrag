<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import BasicConfig from './components/BasicConfig.vue'
import KnowledgeConfig from './components/KnowledgeConfig.vue'
import SkillConfig from './components/SkillConfig.vue'
import WidgetConfig from './components/WidgetConfig.vue'
import ToolConfig from './components/ToolConfig.vue'
import McpConfig from './components/McpConfig.vue'
import DatabaseConfig from './components/DatabaseConfig.vue'
import GlobalConfig from './components/GlobalConfig.vue'
import ChatLogs from './components/ChatLogs.vue'
import MemberConfig from './components/MemberConfig.vue'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()
const appId = route.params.id as string
const activeMenu = ref('basic')

// ===========================================================================
// 菜单（一级平铺，不分组）
// ===========================================================================
const menuItems = [
  { key: 'basic', label: '基础配置', icon: 'Setting' },
  { key: 'kb', label: '知识库配置', icon: 'Collection' },
  { key: 'skill', label: '技能', icon: 'MagicStick' },
  { key: 'tool', label: '工具', icon: 'SetUp' },
  { key: 'mcp', label: 'MCP', icon: 'Connection' },
  { key: 'database', label: '数据库', icon: 'Coin' },
  { key: 'widget', label: 'Widget', icon: 'Postcard' },
  { key: 'member', label: '成员', icon: 'User' },
  { key: 'global', label: '全局策略', icon: 'Operation' },
  { key: 'chat-log', label: '对话记录', icon: 'Document' },
]

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
        <div class="menu-list">
          <div
            v-for="item in menuItems"
            :key="item.key"
            class="menu-item"
            :class="{ active: activeMenu === item.key }"
            @click="activeMenu = item.key"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
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
      <DatabaseConfig v-if="activeMenu === 'database'" :app-info="appInfo" />
      <WidgetConfig v-if="activeMenu === 'widget'" :app-info="appInfo" />
      <MemberConfig v-if="activeMenu === 'member'" :app-info="appInfo" />
      <GlobalConfig v-if="activeMenu === 'global'" :app-info="appInfo" />
      <!-- 运营 -->
      <ChatLogs v-if="activeMenu === 'chat-log'" :app-info="appInfo" />

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

.menu-list {
  padding: $spacing-sm 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-base;
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

.editor-content {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg;
}
</style>
