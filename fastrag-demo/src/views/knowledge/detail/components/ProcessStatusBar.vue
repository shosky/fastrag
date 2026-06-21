<script setup lang="ts">
import { computed } from 'vue'
import type { ProcessStatus } from '@/types/knowledge'

const props = defineProps<{
  status: ProcessStatus
  progress: number
  stage?: string
  category?: string
}>()

const statusConfig = computed(() => {
  const map: Record<ProcessStatus, { color: string; label: string; icon: string }> = {
    pending: { color: '#909399', label: '待处理', icon: 'Clock' },
    processing: { color: '#409eff', label: '处理中', icon: 'Loading' },
    completed: { color: '#67c23a', label: '已完成', icon: 'CircleCheck' },
    failed: { color: '#f56c6c', label: '失败', icon: 'CircleClose' },
  }
  return map[props.status]
})

const stageSteps = computed(() => {
  const stageMap: Record<string, string[]> = {
    document: ['文本提取', '分块', 'Embedding', '存储'],
    image: ['OCR识别', '描述生成', 'Embedding', '存储'],
    audio: ['ASR转写', '文本清理', '分块', 'Embedding', '存储'],
    video: ['关键帧提取', '帧理解', 'ASR转写', '合并', '分块', 'Embedding', '存储'],
  }
  return stageMap[props.category || 'document'] || stageMap.document
})

const currentStageIndex = computed(() => {
  if (!props.stage) return -1
  // 模糊匹配：容忍"分块中"等变体，避免后端返回与展示字符串不一致导致全部 pending
  const idx = stageSteps.value.findIndex(s =>
    s === props.stage || s.includes(props.stage!) || props.stage!.includes(s),
  )
  return idx
})
</script>

<template>
  <div class="process-status-bar">
    <template v-if="status === 'processing'">
      <div class="progress-info">
        <el-progress
          :percentage="progress"
          :stroke-width="8"
          :color="statusConfig.color"
          style="flex: 1"
        />
        <span class="stage-text">{{ stage || '处理中' }}</span>
      </div>
      <div class="stage-steps">
        <div
          v-for="(step, index) in stageSteps"
          :key="step"
          class="stage-step"
          :class="{
            completed: index < currentStageIndex,
            active: index === currentStageIndex,
            pending: index > currentStageIndex
          }"
        >
          <el-icon v-if="index < currentStageIndex"><CircleCheck /></el-icon>
          <el-icon v-else-if="index === currentStageIndex" class="rotating"><Loading /></el-icon>
          <el-icon v-else><Clock /></el-icon>
          <span>{{ step }}</span>
        </div>
      </div>
    </template>
    <template v-else>
      <el-tag :type="status === 'completed' ? 'success' : status === 'failed' ? 'danger' : 'info'" size="small">
        <el-icon><component :is="statusConfig.icon" /></el-icon>
        {{ statusConfig.label }}
      </el-tag>
      <!-- 失败时显示失败的阶段 -->
      <span v-if="status === 'failed' && stage" class="failed-stage">
        失败于：{{ stage }}
      </span>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.process-status-bar {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 180px;
}

.failed-stage {
  font-size: 11px;
  color: #f56c6c;
  margin-top: 2px;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 8px;

  .stage-text {
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
  }
}

.stage-steps {
  display: flex;
  gap: 4px;
  flex-wrap: nowrap;
  overflow: hidden;

  .stage-step {
    display: flex;
    align-items: center;
    gap: 2px;
    font-size: 10px;
    padding: 1px 4px;
    border-radius: 3px;
    background: #f5f7fa;
    color: #909399;
    white-space: nowrap;

    &.completed {
      background: #f0f9eb;
      color: #67c23a;
    }

    &.active {
      background: #ecf5ff;
      color: #409eff;
    }
  }
}

.rotating {
  animation: rotating 1s linear infinite;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
