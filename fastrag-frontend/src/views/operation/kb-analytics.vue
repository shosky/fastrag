<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import KnowledgeAssetPanel from './components/KnowledgeAssetPanel.vue'
import RetrievalLogPanel from './components/RetrievalLogPanel.vue'

const route = useRoute()

// 深链兼容：/operation/retrieval-analysis 重定向至此并带 tab=retrieval
const activeTab = ref(route.query.tab === 'retrieval' ? 'retrieval' : 'assets')
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" class="analytics-tabs">
      <el-tab-pane label="知识资产" name="assets" lazy>
        <KnowledgeAssetPanel />
      </el-tab-pane>
      <el-tab-pane label="检索日志" name="retrieval" lazy>
        <RetrievalLogPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.analytics-tabs {
  :deep(.el-tabs__content) {
    padding-top: $spacing-base;
  }
}
</style>
