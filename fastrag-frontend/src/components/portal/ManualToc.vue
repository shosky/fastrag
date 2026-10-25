<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ManualGroup } from '@/utils/manualToc'

const props = defineProps<{
  groups: ManualGroup[]
  activeId?: string
  version?: string
}>()

// 分组折叠：默认全部展开（MaxKB 文档站样式）
const groupOpen = ref<Record<string, boolean>>(
  Object.fromEntries(props.groups.map((g) => [g.title, true]))
)
// 章内节折叠：默认收起，滚动联动到某章时自动展开该章
const expandedChapters = ref<Set<string>>(new Set())

watch(
  () => props.groups,
  (groups) => {
    groupOpen.value = Object.fromEntries(groups.map((g) => [g.title, true]))
  }
)

watch(
  () => props.activeId,
  (id) => {
    if (!id) return
    expandedChapters.value.add(id)
  },
  { immediate: true }
)

function toggleGroup(title: string) {
  groupOpen.value[title] = !groupOpen.value[title]
}

function toggleChapter(id: string) {
  const next = new Set(expandedChapters.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedChapters.value = next
}

function scrollTo(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <nav class="manual-toc" aria-label="手册目录">
    <!-- 版本头卡：产品名 + 当前版本（MaxKB 文档站样式） -->
    <div class="manual-toc__header">
      <span class="manual-toc__product">FastRAG 用户手册</span>
      <el-tag v-if="version" size="small" effect="plain" class="manual-toc__version">{{ version }}</el-tag>
    </div>

    <ul class="manual-toc__list">
      <li v-for="group in groups" :key="group.title">
        <div
          class="manual-toc__group"
          :class="{ open: groupOpen[group.title] }"
          @click="toggleGroup(group.title)"
        >
          <span>{{ group.title }}</span>
          <el-icon :size="12" class="manual-toc__chevron">
            <ArrowDown />
          </el-icon>
        </div>

        <ul v-show="groupOpen[group.title]" class="manual-toc__chapters">
          <li v-for="chapter in group.chapters" :key="chapter.id">
            <div class="manual-toc__chapter" :class="{ active: chapter.id === activeId }">
              <a
                :href="'#' + chapter.id"
                class="manual-toc__chapter-link"
                @click.prevent="scrollTo(chapter.id)"
              >
                {{ chapter.text }}
              </a>
              <span
                v-if="chapter.children.length"
                class="manual-toc__chapter-toggle"
                :class="{ open: expandedChapters.has(chapter.id) }"
                @click.stop="toggleChapter(chapter.id)"
              >
                <el-icon :size="12"><ArrowRight /></el-icon>
              </span>
            </div>
            <ul v-show="expandedChapters.has(chapter.id)" class="manual-toc__sections">
              <li v-for="section in chapter.children" :key="section.id">
                <a
                  :href="'#' + section.id"
                  class="manual-toc__section"
                  :class="{ active: section.id === activeId }"
                  @click.prevent="scrollTo(section.id)"
                >
                  {{ section.text }}
                </a>
              </li>
            </ul>
          </li>
        </ul>
      </li>
    </ul>
  </nav>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.manual-toc__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
  padding: 10px $spacing-base;
  border: 1px solid $border-base;
  border-radius: $radius-base;
  background: $bg-white;
  margin-bottom: $spacing-base;
}

.manual-toc__product {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.manual-toc__list,
.manual-toc__chapters,
.manual-toc__sections {
  list-style: none;
  margin: 0;
  padding: 0;
}

.manual-toc__group {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px $spacing-sm;
  border-radius: $radius-sm;
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  cursor: pointer;
  user-select: none;

  &:hover {
    background: $bg-hover;
  }
}

.manual-toc__chevron {
  color: $text-placeholder;
  transition: transform 0.2s;
}

.manual-toc__group.open .manual-toc__chevron {
  transform: rotate(0deg);
}

.manual-toc__group:not(.open) .manual-toc__chevron {
  transform: rotate(-90deg);
}

.manual-toc__chapters {
  margin: 2px 0 6px;
}

.manual-toc__chapter {
  display: flex;
  align-items: center;
  gap: 2px;
  border-radius: $radius-sm;

  &.active {
    background: $color-primary-light;

    .manual-toc__chapter-link {
      color: $color-primary;
      font-weight: 600;
    }
  }
}

.manual-toc__chapter-link {
  flex: 1;
  min-width: 0;
  padding: 5px $spacing-sm;
  border-radius: $radius-sm;
  font-size: 13px;
  color: $text-regular;
  text-decoration: none;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &:hover {
    color: $color-primary;
  }
}

.manual-toc__chapter-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-right: 4px;
  border-radius: 4px;
  color: $text-placeholder;
  cursor: pointer;
  flex-shrink: 0;

  .el-icon {
    transition: transform 0.2s;
  }

  &:hover {
    background: $bg-hover;
    color: $text-regular;
  }

  &.open .el-icon {
    transform: rotate(90deg);
  }
}

.manual-toc__sections {
  padding-left: 14px;
}

.manual-toc__section {
  display: block;
  padding: 4px $spacing-sm;
  border-radius: $radius-sm;
  font-size: 12px;
  color: $text-secondary;
  text-decoration: none;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &:hover {
    color: $color-primary;
  }

  &.active {
    color: $color-primary;
    font-weight: 600;
  }
}
</style>
