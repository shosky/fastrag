<script setup lang="ts">
import { ref } from 'vue'

const quickEntries = ref([
  { title: '知识库', icon: 'Collection', color: '#409eff', path: '/knowledge' },
  { title: '应用中心', icon: 'Grid', color: '#67c23a', path: '/application' },
  { title: '知识加工', icon: 'Setting', color: '#e6a23c', path: '/process' },
  { title: '运营中心', icon: 'TrendCharts', color: '#f56c6c', path: '/operation/kb-analytics' },
])

const recommendKBs = ref([
  { id: '1', name: '产品知识库', description: '包含所有产品文档和操作手册', tags: ['产品', '文档'] },
  { id: '2', name: '技术知识库', description: '技术架构、API 文档和开发规范', tags: ['技术', 'API'] },
  { id: '3', name: '客户案例库', description: '客户成功案例和最佳实践', tags: ['案例', '客户'] },
])

const hotDocs = ref([
  { name: 'AIS 平台用户手册.pdf', owner: '管理员', viewCount: 1280, lastView: '2026-06-04 10:30' },
  { name: '产品使用指南.docx', owner: '产品经理', viewCount: 856, lastView: '2026-06-04 09:15' },
  { name: 'API 接口文档.md', owner: '开发工程师', viewCount: 623, lastView: '2026-06-03 16:45' },
  { name: '部署运维手册.pdf', owner: '运维工程师', viewCount: 445, lastView: '2026-06-03 14:20' },
])

const hotApps = ref([
  { name: '智能问答助手', type: 'ChatBot', description: '基于知识库的智能问答应用' },
  { name: '文档写作助手', type: 'Editor', description: 'AI 辅助的文档创作工具' },
  { name: '客服机器人', type: 'LiteAgent', description: '企业客服智能体应用' },
])
</script>

<template>
  <div class="page-container">
    <!-- 快捷入口 -->
    <div class="card-panel">
      <div class="section-title">快捷入口</div>
      <div class="quick-entries">
        <div
          v-for="entry in quickEntries"
          :key="entry.title"
          class="entry-card"
          @click="$router.push(entry.path)"
        >
          <div class="entry-icon" :style="{ background: entry.color }">
            <el-icon :size="28"><component :is="entry.icon" /></el-icon>
          </div>
          <span class="entry-title">{{ entry.title }}</span>
        </div>
      </div>
    </div>

    <div class="home-grid">
      <!-- 推荐知识库 -->
      <div class="card-panel">
        <div class="section-title">推荐知识库</div>
        <div class="kb-list">
          <div v-for="kb in recommendKBs" :key="kb.id" class="kb-card">
            <div class="kb-name">{{ kb.name }}</div>
            <div class="kb-desc">{{ kb.description }}</div>
            <div class="kb-tags">
              <el-tag v-for="tag in kb.tags" :key="tag" size="small" type="info">
                {{ tag }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 热门文档 -->
      <div class="card-panel">
        <div class="section-title">热门文档</div>
        <el-table :data="hotDocs" stripe size="small">
          <el-table-column type="index" width="50" />
          <el-table-column prop="name" label="文档名称" show-overflow-tooltip />
          <el-table-column prop="owner" label="所有者" width="100" />
          <el-table-column prop="viewCount" label="浏览次数" width="90" align="center" />
          <el-table-column prop="lastView" label="最近查看" width="160" />
        </el-table>
      </div>

      <!-- 热门应用 -->
      <div class="card-panel">
        <div class="section-title">热门应用</div>
        <div class="app-list">
          <div v-for="app in hotApps" :key="app.name" class="app-card">
            <el-icon :size="32" color="#409eff"><ChatDotRound /></el-icon>
            <div class="app-info">
              <div class="app-name">{{ app.name }}</div>
              <div class="app-desc">{{ app.description }}</div>
            </div>
            <el-tag size="small">{{ app.type }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 知识动态 -->
      <div class="card-panel">
        <div class="section-title">知识动态</div>
        <el-timeline>
          <el-timeline-item timestamp="2026-06-04 10:30" placement="top">
            <p>管理员 更新了 <strong>产品知识库</strong> 中的 3 篇文档</p>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-06-04 09:15" placement="top">
            <p>产品经理 创建了新知识库 <strong>客户案例库</strong></p>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-06-03 16:00" placement="top">
            <p>开发工程师 上传了 <strong>API 接口文档 v2.0</strong></p>
          </el-timeline-item>
          <el-timeline-item timestamp="2026-06-03 14:30" placement="top">
            <p>系统自动 完成了 <strong>飞书知识库</strong> 数据源同步</p>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.quick-entries {
  display: flex;
  gap: $spacing-xl;
}

.entry-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  cursor: pointer;

  &:hover .entry-icon {
    transform: scale(1.1);
  }
}

.entry-icon {
  width: 56px;
  height: 56px;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: transform 0.2s;
}

.entry-title {
  font-size: 13px;
  color: $text-regular;
}

.home-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-base;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.kb-card {
  padding: $spacing-base;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  cursor: pointer;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: $shadow-base;
  }
}

.kb-name {
  font-weight: 600;
  margin-bottom: $spacing-xs;
}

.kb-desc {
  font-size: 12px;
  color: $text-secondary;
  margin-bottom: $spacing-sm;
}

.kb-tags {
  display: flex;
  gap: $spacing-xs;
}

.app-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.app-card {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  padding: $spacing-base;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  cursor: pointer;

  &:hover {
    box-shadow: $shadow-base;
  }
}

.app-info {
  flex: 1;
}

.app-name {
  font-weight: 600;
  margin-bottom: $spacing-xs;
}

.app-desc {
  font-size: 12px;
  color: $text-secondary;
}
</style>
