<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as api from '@/api'

const router = useRouter()
const loading = ref(true)

const quickEntries = ref([
  { title: '知识库', icon: 'Collection', color: '#409eff', path: '/knowledge' },
  { title: '应用中心', icon: 'Grid', color: '#67c23a', path: '/application' },
  { title: '知识加工', icon: 'Setting', color: '#e6a23c', path: '/process' },
  { title: '运营中心', icon: 'TrendCharts', color: '#f56c6c', path: '/operation/kb-analytics' },
])

const recommendKBs = ref<any[]>([])
const hotDocs = ref<any[]>([])
const hotApps = ref<any[]>([])
const recentActivities = ref<any[]>([])

async function loadHomeData() {
  loading.value = true
  try {
    const data: any = await api.getHomeData()
    if (data) {
      recommendKBs.value = data.recommendKBs || []
      hotDocs.value = data.hotDocs || []
      hotApps.value = data.hotApps || []
      recentActivities.value = data.recentActivities || []
    }
  } catch {
    // 加载失败，保持空数据
  } finally {
    loading.value = false
  }
}

onMounted(loadHomeData)

function goToKb(kbId: string) {
  router.push(`/knowledge/${kbId}`)
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 快捷入口 -->
    <div class="card-panel">
      <div class="section-title">快捷入口</div>
      <div class="quick-entries">
        <div
          v-for="entry in quickEntries"
          :key="entry.title"
          class="entry-card"
          @click="router.push(entry.path)"
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
        <div class="kb-list" v-if="recommendKBs.length">
          <div v-for="kb in recommendKBs" :key="kb.id" class="kb-card" @click="goToKb(kb.id)">
            <div class="kb-name">{{ kb.name }}</div>
            <div class="kb-desc">{{ kb.description }}</div>
            <div class="kb-tags">
              <el-tag v-for="tag in kb.tags" :key="tag" size="small" type="info">
                {{ tag }}
              </el-tag>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无知识库" :image-size="60" />
      </div>

      <!-- 热门文档 -->
      <div class="card-panel">
        <div class="section-title">热门文档</div>
        <el-table v-if="hotDocs.length" :data="hotDocs" stripe size="small">
          <el-table-column type="index" width="50" />
          <el-table-column prop="name" label="文档名称" show-overflow-tooltip />
          <el-table-column prop="viewCount" label="浏览次数" width="90" align="center" />
          <el-table-column prop="updatedAt" label="更新时间" width="160" />
        </el-table>
        <el-empty v-else description="暂无文档" :image-size="60" />
      </div>

      <!-- 热门应用 -->
      <div class="card-panel">
        <div class="section-title">热门应用</div>
        <div class="app-list" v-if="hotApps.length">
          <div v-for="app in hotApps" :key="app.id" class="app-card">
            <el-icon :size="32" color="#409eff"><ChatDotRound /></el-icon>
            <div class="app-info">
              <div class="app-name">{{ app.name }}</div>
              <div class="app-desc">{{ app.description }}</div>
            </div>
            <el-tag size="small">{{ app.type }}</el-tag>
          </div>
        </div>
        <el-empty v-else description="暂无应用" :image-size="60" />
      </div>

      <!-- 知识动态 -->
      <div class="card-panel">
        <div class="section-title">知识动态</div>
        <el-timeline v-if="recentActivities.length">
          <el-timeline-item
            v-for="log in recentActivities"
            :key="log.id"
            :timestamp="log.timestamp"
            placement="top"
          >
            <p>{{ log.operator }} {{ log.action }} <strong>{{ log.target }}</strong></p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无动态" :image-size="60" />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

// 首页网格中的卡片保留独立卡片样式（因为多卡并排，需要视觉边界）
.home-grid .card-panel {
  background: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-xl;
  border: 1px solid $border-light;
}

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
