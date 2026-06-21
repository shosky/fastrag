<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// 最近访问
const recentVisits = ref([
  { id: '1', name: 'AIS 平台用户手册.pdf', kbName: '产品知识库', modifyTime: '2026-06-04 10:30', visitTime: '2026-06-04 11:00' },
  { id: '2', name: 'API 接口文档 v2.0.md', kbName: '技术知识库', modifyTime: '2026-06-03 16:45', visitTime: '2026-06-04 09:30' },
  { id: '3', name: '产品使用指南.docx', kbName: '产品知识库', modifyTime: '2026-06-02 14:20', visitTime: '2026-06-03 15:00' },
  { id: '4', name: '部署运维手册.pdf', kbName: '技术知识库', modifyTime: '2026-06-01 09:10', visitTime: '2026-06-02 11:20' },
  { id: '5', name: '客户需求分析.xlsx', kbName: '客户案例库', modifyTime: '2026-05-30 16:00', visitTime: '2026-06-01 10:15' },
])

// 推荐知识库
const recommendKBs = ref([
  { id: '1', name: '产品知识库', description: '包含所有产品文档和操作手册', tags: ['产品', '文档'] },
  { id: '2', name: '技术知识库', description: '技术架构、API 文档和开发规范', tags: ['技术', 'API'] },
  { id: '3', name: '客户案例库', description: '客户成功案例和最佳实践', tags: ['案例', '客户'] },
])

// 热门应用
const hotApps = ref([
  { id: '1', name: '智能问答助手', type: 'ChatBot', icon: '#409eff' },
  { id: '2', name: '文档写作助手', type: 'Editor', icon: '#67c23a' },
  { id: '3', name: '客服机器人', type: 'LiteAgent', icon: '#e6a23c' },
])

// 热门知识库
const hotKBs = ref([
  { id: '1', name: '产品知识库', docCount: 156, viewCount: 3280 },
  { id: '2', name: '技术知识库', docCount: 89, viewCount: 2150 },
  { id: '3', name: '客户案例库', docCount: 45, viewCount: 1890 },
])

// 我的关注
const myFollows = ref([
  { id: '1', name: 'AIS 平台用户手册.pdf', type: '文档', kbName: '产品知识库' },
  { id: '2', name: '产品知识库', type: '知识库', kbName: '' },
  { id: '3', name: 'API 接口文档.md', type: '文档', kbName: '技术知识库' },
])

// 我的收藏
const myFavorites = ref([
  { id: '1', name: '部署运维手册.pdf', kbName: '技术知识库' },
  { id: '2', name: '客户需求分析.xlsx', kbName: '客户案例库' },
])

// 推荐知识库设置弹窗
const showRecommendDialog = ref(false)
const newRecommend = ref({ title: '', url: '', imageUrl: '' })

function handleAddRecommend() {
  // TODO: 添加推荐知识库
  showRecommendDialog.value = false
}

function handleRemoveFavorite(id: string) {
  myFavorites.value = myFavorites.value.filter(item => item.id !== id)
}
</script>

<template>
  <div class="page-container">
    <!-- 顶部信息栏 -->
    <div class="workspace-header">
      <div class="header-left">
        <h2>我的工作台</h2>
        <span class="capacity-info">个人知识库容量：已使用 1.54 MB / 10 GB</span>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="router.push('/workspace/custom')">
          <el-icon><Setting /></el-icon>设置
        </el-button>
      </div>
    </div>

    <div class="workspace-grid">
      <!-- 最近访问 -->
      <div class="card-panel">
        <div class="section-title">
          <el-icon><Clock /></el-icon>最近访问
        </div>
        <el-table :data="recentVisits" stripe size="small">
          <el-table-column prop="name" label="文档名称" show-overflow-tooltip />
          <el-table-column prop="kbName" label="所属知识库" width="120" />
          <el-table-column prop="modifyTime" label="修改时间" width="160" />
          <el-table-column prop="visitTime" label="访问时间" width="160" />
        </el-table>
      </div>

      <!-- 推荐知识库 -->
      <div class="card-panel">
        <div class="section-title">
          <el-icon><Star /></el-icon>推荐知识库
          <el-button link type="primary" size="small" @click="showRecommendDialog = true">
            <el-icon><Edit /></el-icon>设置
          </el-button>
        </div>
        <div class="kb-cards">
          <div v-for="kb in recommendKBs" :key="kb.id" class="kb-card">
            <div class="kb-name">{{ kb.name }}</div>
            <div class="kb-desc">{{ kb.description }}</div>
            <div class="kb-tags">
              <el-tag v-for="tag in kb.tags" :key="tag" size="small" type="info">{{ tag }}</el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 热门应用 -->
      <div class="card-panel">
        <div class="section-title">
          <el-icon><Grid /></el-icon>热门应用
        </div>
        <div class="app-cards">
          <div v-for="app in hotApps" :key="app.id" class="app-card">
            <div class="app-icon" :style="{ background: app.icon }">
              <el-icon :size="24" color="#fff"><ChatDotRound /></el-icon>
            </div>
            <div class="app-info">
              <div class="app-name">{{ app.name }}</div>
              <el-tag size="small">{{ app.type }}</el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 热门知识库 -->
      <div class="card-panel">
        <div class="section-title">
          <el-icon><TrendCharts /></el-icon>热门知识库
        </div>
        <div class="kb-list">
          <div v-for="(kb, index) in hotKBs" :key="kb.id" class="kb-item">
            <span class="rank" :class="{ 'top-3': index < 3 }">{{ index + 1 }}</span>
            <span class="name">{{ kb.name }}</span>
            <span class="count">{{ kb.docCount }} 篇文档</span>
            <span class="views">{{ kb.viewCount }} 次浏览</span>
          </div>
        </div>
      </div>

      <!-- 我的关注 -->
      <div class="card-panel">
        <div class="section-title">
          <el-icon><View /></el-icon>我的关注
        </div>
        <el-tabs>
          <el-tab-pane label="知识库">
            <div v-for="item in myFollows.filter(f => f.type === '知识库')" :key="item.id" class="follow-item">
              <el-icon><Collection /></el-icon>
              <span>{{ item.name }}</span>
            </div>
            <el-empty v-if="!myFollows.filter(f => f.type === '知识库').length" description="暂无关注的知识库" :image-size="60" />
          </el-tab-pane>
          <el-tab-pane label="文档">
            <div v-for="item in myFollows.filter(f => f.type === '文档')" :key="item.id" class="follow-item">
              <el-icon><Document /></el-icon>
              <span>{{ item.name }}</span>
              <el-tag size="small" type="info">{{ item.kbName }}</el-tag>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 我的收藏 -->
      <div class="card-panel">
        <div class="section-title">
          <el-icon><Collection /></el-icon>我的收藏
        </div>
        <div v-for="item in myFavorites" :key="item.id" class="favorite-item">
          <div class="favorite-info">
            <el-icon><Document /></el-icon>
            <span class="name">{{ item.name }}</span>
            <el-tag size="small" type="info">{{ item.kbName }}</el-tag>
          </div>
          <el-button link type="danger" size="small" @click="handleRemoveFavorite(item.id)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <el-empty v-if="!myFavorites.length" description="暂无收藏内容" :image-size="60" />
      </div>
    </div>

    <!-- 推荐知识库设置弹窗 -->
    <el-dialog v-model="showRecommendDialog" title="推荐知识库设置" width="500px">
      <el-form label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="newRecommend.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="URL">
          <el-input v-model="newRecommend.url" placeholder="请输入跳转地址" />
        </el-form-item>
        <el-form-item label="图片">
          <el-upload action="#" list-type="picture-card" :auto-upload="false">
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRecommendDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddRecommend">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;

  h2 {
    margin: 0;
    font-size: 20px;
  }

  .capacity-info {
    font-size: 13px;
    color: $text-secondary;
    margin-left: $spacing-base;
  }
}

.workspace-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-base;
}

.section-title {
  display: flex;
  align-items: center;
  gap: $spacing-sm;

  .el-button {
    margin-left: auto;
  }
}

.kb-cards {
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
}

.app-cards {
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

  .app-icon {
    width: 48px;
    height: 48px;
    border-radius: $radius-base;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .app-name {
    font-weight: 600;
    margin-bottom: $spacing-xs;
  }
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  padding: $spacing-sm 0;

  .rank {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: $border-lighter;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    color: $text-secondary;

    &.top-3 {
      background: $color-primary;
      color: #fff;
    }
  }

  .name {
    flex: 1;
    font-weight: 500;
  }

  .count, .views {
    font-size: 12px;
    color: $text-secondary;
  }
}

.follow-item, .favorite-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm 0;
  border-bottom: 1px solid $border-extra-light;

  &:last-child {
    border-bottom: none;
  }
}

.favorite-item {
  justify-content: space-between;

  .favorite-info {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }
}
</style>
