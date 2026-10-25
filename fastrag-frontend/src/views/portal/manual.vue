<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { injectHeadingIds, parseManualGroups } from '@/utils/manualToc'
import ManualToc from '@/components/portal/ManualToc.vue'
import manualRaw from '@/content/manual.md?raw'
import { CHANGELOG } from '@/constants/changelog'

// 目录：部分分组 → 章 → 节（唯一转换点 parseManualGroups，锚点 id 与正文注入一致）
const groups = computed(() => parseManualGroups(manualRaw))
const version = CHANGELOG[0]?.version

// STATE-001 渲染回退态：解析异常时回退显示原文，不白屏
const html = computed(() => {
  try {
    return injectHeadingIds(renderMarkdown(manualRaw))
  } catch {
    return ''
  }
})
const renderFailed = computed(() => html.value === '')

// 滚动联动高亮：client 局部状态（STATE 归属表——手册目录滚动高亮归 client）
const activeId = ref('')
const contentEl = ref<HTMLElement | null>(null)
let rafId = 0

function onScroll() {
  cancelAnimationFrame(rafId)
  rafId = requestAnimationFrame(() => {
    const container = contentEl.value
    if (!container) return
    const headings = container.querySelectorAll<HTMLElement>('.manual-doc h2, .manual-doc h3')
    const threshold = container.scrollTop + 90
    let current = ''
    headings.forEach((h) => {
      if (h.offsetTop <= threshold) current = h.id
    })
    activeId.value = current
  })
}

onMounted(() => {
  contentEl.value?.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  contentEl.value?.removeEventListener('scroll', onScroll)
})

// 移动端章节下拉：与目录点击同一锚点滚动行为
function scrollToChapter(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="manual-page">
    <!-- ≤1024px：目录折叠为顶部章节下拉 -->
    <div class="manual-page__toc-mobile">
      <el-select
        class="manual-page__toc-select"
        placeholder="选择章节"
        :model-value="activeId"
        @change="scrollToChapter"
      >
        <el-option
          v-for="group in groups"
          :key="group.title"
          :label="group.title"
          :disabled="!group.chapters.length"
        >
          <el-option
            v-for="chapter in group.chapters"
            :key="chapter.id"
            :label="chapter.text"
            :value="chapter.id"
          />
        </el-option>
      </el-select>
    </div>

    <aside class="manual-page__toc">
      <ManualToc :groups="groups" :active-id="activeId" :version="version" />
    </aside>

    <div ref="contentEl" class="manual-page__content">
      <!-- STATE-001：渲染异常回退原文 -->
      <pre v-if="renderFailed" class="manual-page__fallback">{{ manualRaw }}</pre>
      <article v-else class="manual-doc" v-html="html"></article>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.manual-page {
  flex: 1;
  display: flex;
  min-height: 0;
}

.manual-page__toc {
  width: 248px;
  flex-shrink: 0;
  padding: $spacing-lg $spacing-base;
  border-right: 1px solid $border-light;
  background: $bg-white;
  overflow-y: auto;
  max-height: calc(100vh - 56px);
}

.manual-page__content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  max-height: calc(100vh - 56px);
  background: $bg-white;
}

.manual-page__toc-mobile {
  display: none;
}

.manual-page__fallback {
  white-space: pre-wrap;
  word-break: break-word;
  padding: $spacing-xl;
  font-size: 13px;
  color: $text-regular;
}

// 阅读排版（独立样式类，不套聊天气泡样式）
.manual-doc {
  max-width: 860px;
  margin: 0 auto;
  padding: $spacing-xxl $spacing-xl 80px;
  color: $text-regular;
  font-size: 14px;
  line-height: 1.8;

  :deep(h1) {
    font-size: 26px;
    font-weight: 700;
    color: $text-primary;
    margin: $spacing-lg 0 $spacing-base;
  }

  :deep(h2) {
    font-size: 20px;
    font-weight: 600;
    color: $text-primary;
    margin: $spacing-xxl 0 $spacing-base;
    padding-bottom: $spacing-sm;
    border-bottom: 1px solid $border-light;
  }

  :deep(h3) {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin: $spacing-xl 0 $spacing-sm;
  }

  :deep(h4) {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    margin: $spacing-lg 0 $spacing-sm;
  }

  :deep(p) {
    margin: $spacing-sm 0;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: $spacing-xl;
    margin: $spacing-sm 0;
  }

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: $spacing-base 0;

    th,
    td {
      border: 1px solid $border-base;
      padding: 6px $spacing-sm;
      text-align: left;
      font-size: 13px;
    }

    th {
      background: $bg-hover;
      font-weight: 600;
    }
  }

  :deep(code) {
    background: $bg-hover;
    border: 1px solid $border-lighter;
    border-radius: $radius-sm;
    padding: 1px 5px;
    font-size: 13px;
  }

  :deep(pre) {
    background: $bg-hover;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    padding: $spacing-base;
    overflow-x: auto;

    code {
      border: none;
      background: transparent;
      padding: 0;
    }
  }

  :deep(blockquote) {
    margin: $spacing-base 0;
    padding: $spacing-sm $spacing-base;
    border-left: 3px solid $color-primary;
    background: $color-primary-light;
    color: $text-secondary;
  }
}

@media (max-width: 1024px) {
  .manual-page {
    flex-direction: column;
  }

  .manual-page__toc {
    display: none;
  }

  .manual-page__toc-mobile {
    display: block;
    padding: $spacing-sm $spacing-base;
    background: $bg-white;
    border-bottom: 1px solid $border-light;
  }

  .manual-page__content {
    max-height: none;
  }
}
</style>
