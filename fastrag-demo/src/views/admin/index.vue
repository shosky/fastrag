<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const modules = [
  {
    title: '系统管理',
    icon: 'Setting',
    color: '#409eff',
    description: '维护平台基础配置、知识库规则、敏感词、字典和术语等系统能力。',
    children: [
      { name: '通用设置', path: '/admin/system/general-settings' },
      { name: '知识库配置', path: '/admin/system/kb-config' },
      { name: '全局设置', path: '/admin/system/global-settings' },
      { name: '敏感词设置', path: '/admin/system/sensitive-words' },
      { name: '字典管理', path: '/admin/system/dictionary' },
      { name: '术语管理', path: '/admin/system/terminology' },
    ],
  },
  {
    title: '账号权限',
    icon: 'User',
    color: '#67c23a',
    description: '维护角色、组织、团队和人员，确保平台权限结构与组织关系保持一致。',
    children: [
      { name: '角色管理', path: '/admin/account/roles' },
      { name: '组织管理', path: '/admin/account/organization' },
      { name: '团队管理', path: '/admin/account/team' },
      { name: '人员管理', path: '/admin/account/personnel' },
    ],
  },
  {
    title: '安全审计',
    icon: 'Lock',
    color: '#e6a23c',
    description: '查看系统日志、登录设备与安全策略配置，用于风险排查和安全治理。',
    children: [
      { name: '系统日志', path: '/admin/audit/system-log' },
      { name: '设备登录分析', path: '/admin/audit/device-login' },
      { name: '登录安全配置', path: '/admin/audit/login-security' },
    ],
  },
  {
    title: '内容与工具',
    icon: 'Files',
    color: '#f56c6c',
    description: '维护通知、标签、提示词、文档模板和下载中心等内容工具能力。',
    children: [
      { name: '通知管理', path: '/admin/content/notification' },
      { name: '标签管理', path: '/admin/content/tags' },
      { name: '提示词', path: '/admin/content/prompts' },
      { name: '文档模板', path: '/admin/content/templates' },
      { name: '下载中心', path: '/admin/content/download' },
    ],
  },
  {
    title: '开放平台',
    icon: 'Connection',
    color: '#909399',
    description: '管理三方平台接入、模型配置和开放密钥等对外能力。',
    children: [
      { name: '三方平台', path: '/admin/platform/third-party' },
      { name: '模型管理', path: '/admin/platform/model-management' },
      { name: '开放密钥', path: '/admin/platform/api-keys' },
    ],
  },
]
</script>

<template>
  <div class="page-container">
    <div class="admin-header">
      <h2>管理中心</h2>
      <p>集中维护系统配置、账号权限、安全审计、内容工具以及开放能力。</p>
    </div>

    <div class="module-grid">
      <div v-for="mod in modules" :key="mod.title" class="module-card">
        <div class="module-header">
          <div class="module-icon" :style="{ background: mod.color }">
            <el-icon :size="28" color="#fff"><component :is="mod.icon" /></el-icon>
          </div>
          <h3>{{ mod.title }}</h3>
        </div>
        <p class="module-desc">{{ mod.description }}</p>
        <div class="module-links">
          <div
            v-for="child in mod.children"
            :key="child.path"
            class="link-item"
            @click="router.push(child.path)"
          >
            <el-icon><ArrowRight /></el-icon>
            <span>{{ child.name }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.admin-header {
  margin-bottom: $spacing-xl;
  h2 { margin: 0 0 $spacing-sm; }
  p { color: $text-secondary; margin: 0; }
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: $spacing-lg;
}

.module-card {
  background: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-xl;
  box-shadow: $shadow-sm;
  transition: all 0.2s;

  &:hover {
    box-shadow: $shadow-base;
    transform: translateY(-2px);
  }
}

.module-header {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  margin-bottom: $spacing-base;

  h3 { margin: 0; font-size: 18px; }
}

.module-icon {
  width: 48px;
  height: 48px;
  border-radius: $radius-base;
  display: flex;
  align-items: center;
  justify-content: center;
}

.module-desc {
  font-size: 13px;
  color: $text-secondary;
  margin-bottom: $spacing-lg;
  line-height: 1.6;
}

.module-links {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-sm;
}

.link-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm;
  border-radius: $radius-sm;
  cursor: pointer;
  font-size: 13px;
  color: $text-regular;
  transition: all 0.2s;

  &:hover {
    background: $bg-hover;
    color: $color-primary;
  }
}
</style>
