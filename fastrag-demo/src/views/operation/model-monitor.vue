<script setup lang="ts">
import { ref } from 'vue'

const timeRange = ref('近7天')

const metrics = ref([
  { label: '总 Token 消耗', value: '1,234,567', change: '+12.3%', trend: 'up' },
  { label: 'API 调用次数', value: '8,560', change: '+5.6%', trend: 'up' },
  { label: '平均响应时间', value: '1.2s', change: '-0.3s', trend: 'down' },
  { label: '总失败次数', value: '23', change: '-8', trend: 'down' },
])

const modelUsage = ref([
  { name: 'qwen3-32b', percentage: 45, token: '556,780' },
  { name: 'DeepSeek-V3', percentage: 30, token: '370,370' },
  { name: 'Kimi-K2', percentage: 15, token: '185,185' },
  { name: '其他', percentage: 10, token: '123,232' },
])

const highConsumeApps = ref([
  { rank: 1, name: '智能问答助手', token: '456,780', cost: '￥2,283.90' },
  { rank: 2, name: '客服机器人', token: '321,456', cost: '￥1,607.28' },
  { rank: 3, name: '文档写作助手', token: '234,567', cost: '￥1,172.84' },
  { rank: 4, name: '手册演示_ChatBot', token: '123,456', cost: '￥617.28' },
  { rank: 5, name: '测试应用', token: '98,308', cost: '￥491.54' },
])

const modelStats = ref([
  { code: 'qwen3-32b', calls: 3450, fails: 12, token: '556,780', cost: '￥2,783.90' },
  { code: 'DeepSeek-V3', calls: 2310, fails: 8, token: '370,370', cost: '￥1,851.85' },
  { code: 'Kimi-K2', calls: 1155, fails: 3, token: '185,185', cost: '￥925.93' },
])

const searchModel = ref('')

const filteredStats = ref(modelStats.value)
function handleSearch() {
  if (searchModel.value) {
    filteredStats.value = modelStats.value.filter(s => s.code.includes(searchModel.value))
  } else {
    filteredStats.value = modelStats.value
  }
}
</script>

<template>
  <div class="page-container">
    <div class="section-header">
      <h3>模型监控分析</h3>
      <el-select v-model="timeRange" size="small" style="width: 120px">
        <el-option label="近7天" value="近7天" />
        <el-option label="近30天" value="近30天" />
        <el-option label="近6个月" value="近6个月" />
      </el-select>
    </div>

    <div class="metric-cards">
      <div v-for="m in metrics" :key="m.label" class="metric-card">
        <div class="metric-label">{{ m.label }}</div>
        <div class="metric-value">{{ m.value }}</div>
        <div class="metric-change" :class="m.trend === 'down' && m.label.includes('失败') ? 'good' : m.trend">
          {{ m.change }}
        </div>
      </div>
    </div>

    <div class="monitor-grid">
      <div class="card-panel">
        <div class="section-title">模型使用分布</div>
        <div v-for="model in modelUsage" :key="model.name" class="usage-item">
          <div class="usage-header">
            <span>{{ model.name }}</span>
            <span>{{ model.percentage }}%</span>
          </div>
          <el-progress :percentage="model.percentage" :show-text="false" />
          <div class="usage-token">Token: {{ model.token }}</div>
        </div>
      </div>

      <div class="card-panel">
        <div class="section-title">高消耗应用排行</div>
        <div v-for="app in highConsumeApps" :key="app.rank" class="rank-item">
          <span class="rank" :class="{ 'top-3': app.rank <= 3 }">{{ app.rank }}</span>
          <span class="name">{{ app.name }}</span>
          <span class="token">{{ app.token }}</span>
          <span class="cost">{{ app.cost }}</span>
        </div>
      </div>
    </div>

    <div class="card-panel">
      <div class="section-title">模型调用统计</div>
      <div class="filter-bar">
        <el-input v-model="searchModel" placeholder="搜索模型 Code" clearable style="width: 200px" @input="handleSearch" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="searchModel = ''; handleSearch()">重置</el-button>
      </div>
      <el-table :data="filteredStats" stripe>
        <el-table-column prop="code" label="模型 Code" />
        <el-table-column prop="calls" label="调用总量" width="120" />
        <el-table-column prop="fails" label="失败量" width="100" />
        <el-table-column prop="token" label="Token 消耗" width="150" />
        <el-table-column prop="cost" label="消耗金额" width="150" />
      </el-table>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;
  h3 { margin: 0; }
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-base;
  margin-bottom: $spacing-base;
}

.metric-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  .metric-label { font-size: 13px; color: $text-secondary; margin-bottom: $spacing-sm; }
  .metric-value { font-size: 28px; font-weight: 700; margin-bottom: $spacing-xs; }
  .metric-change {
    font-size: 12px;
    &.up { color: $color-danger; }
    &.down { color: $color-success; }
    &.good { color: $color-success; }
  }
}

.monitor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-base;
  margin-bottom: $spacing-base;
}

.usage-item {
  margin-bottom: $spacing-base;
  .usage-header { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: $spacing-xs; }
  .usage-token { font-size: 12px; color: $text-secondary; margin-top: $spacing-xs; }
}

.rank-item {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  padding: $spacing-sm 0;
  border-bottom: 1px solid $border-extra-light;
  .rank {
    width: 24px; height: 24px; border-radius: 50%; background: $border-lighter;
    display: flex; align-items: center; justify-content: center;
    font-size: 12px; font-weight: 600; color: $text-secondary;
    &.top-3 { background: $color-primary; color: #fff; }
  }
  .name { flex: 1; font-size: 13px; }
  .token, .cost { font-size: 12px; color: $text-secondary; }
}
</style>
