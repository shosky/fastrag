<script setup lang="ts">
import { CHANGELOG, type ChangelogType } from '@/constants/changelog'

// type 四枚举 → Element Plus 语义色固定映射（CONTEXT.md 决策快照 19）
const TAG_TYPE: Record<ChangelogType, 'primary' | 'success' | 'warning' | 'danger'> = {
  新功能: 'success',
  优化: 'primary',
  修复: 'warning',
  安全: 'danger',
}
</script>

<template>
  <div class="changelog-page">
    <!-- STATE-002 产品动态空态：种子数据保证非空（spec 验收项），空数组兜底 -->
    <el-empty v-if="!CHANGELOG.length" description="暂无产品动态" />
    <el-timeline v-else class="changelog-timeline">
      <el-timeline-item
        v-for="entry in CHANGELOG"
        :key="entry.version"
        :timestamp="entry.date"
        placement="top"
        type="primary"
      >
        <div class="changelog-entry">
          <div class="changelog-entry__head">
            <span class="changelog-entry__version">{{ entry.version }}</span>
            <el-tag size="small" :type="TAG_TYPE[entry.type]">{{ entry.type }}</el-tag>
          </div>
          <h3 class="changelog-entry__title">{{ entry.title }}</h3>
          <ul class="changelog-entry__items">
            <li v-for="item in entry.items" :key="item">{{ item }}</li>
          </ul>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.changelog-page {
  flex: 1;
  padding: $spacing-xl $spacing-base $spacing-xxl;
}

.changelog-timeline {
  max-width: 720px;
  margin: 0 auto;
  padding: $spacing-lg $spacing-xl;
}

.changelog-entry {
  padding: $spacing-base $spacing-lg;
  background: $bg-white;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
}

.changelog-entry__head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.changelog-entry__version {
  font-size: 15px;
  font-weight: 700;
  color: $color-primary;
}

.changelog-entry__title {
  margin: $spacing-sm 0;
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.changelog-entry__items {
  margin: 0;
  padding-left: $spacing-lg;

  li {
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.8;
  }
}

@media (max-width: 768px) {
  .changelog-page {
    padding: $spacing-md $spacing-sm $spacing-xl;
  }
}
</style>
