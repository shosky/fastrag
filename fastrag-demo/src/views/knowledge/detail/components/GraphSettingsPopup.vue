<script setup lang="ts">
import { Close } from '@element-plus/icons-vue'

// --- Props & Emits ---
const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'apply', settings: GraphSettings): void
}>()

// --- Types ---
interface GraphSettings {
  maxNodes: number
  searchDepth: number
  excludeChunkNodes: boolean
}

// --- Settings state ---
const settings = ref<GraphSettings>({
  maxNodes: 100,
  searchDepth: 2,
  excludeChunkNodes: true,
})

// --- Popup visibility ---
const popupVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// --- Apply settings ---
function handleApply() {
  emit('apply', { ...settings.value })
  popupVisible.value = false
}
</script>

<template>
  <Transition name="popup-slide">
    <div v-if="popupVisible" class="graph-settings">
      <!-- Header -->
      <div class="graph-settings__header">
        <h3 class="graph-settings__title">图谱设置</h3>
        <el-button :icon="Close" link @click="popupVisible = false" />
      </div>

      <!-- Body -->
      <div class="graph-settings__body">
        <!-- Max nodes -->
        <div class="graph-settings__field">
          <label class="graph-settings__label">最大节点数 (limit)</label>
          <el-input
            v-model.number="settings.maxNodes"
            type="number"
            :min="10"
            :max="1000"
          />
        </div>

        <!-- Search depth -->
        <div class="graph-settings__field">
          <label class="graph-settings__label">搜索深度 (depth)</label>
          <el-input
            v-model.number="settings.searchDepth"
            type="number"
            :min="1"
            :max="10"
          />
        </div>

        <!-- Exclude Chunk nodes -->
        <div class="graph-settings__field">
          <label class="graph-settings__label">排除 Chunk 节点</label>
          <el-switch v-model="settings.excludeChunkNodes" />
        </div>
      </div>

      <!-- Footer -->
      <div class="graph-settings__footer">
        <el-button type="primary" class="graph-settings__apply-btn" @click="handleApply">
          应用
        </el-button>
      </div>
    </div>
  </Transition>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.graph-settings {
  position: absolute;
  top: 60px;
  right: 100px;
  z-index: 300;
  width: 280px;
  background: $bg-white;
  border-radius: $radius-base;
  box-shadow: $shadow-lg;
  overflow: hidden;

  // --- Header ---
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base $spacing-lg;
    border-bottom: 1px solid $border-lighter;
  }

  &__title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  // --- Body ---
  &__body {
    padding: $spacing-base $spacing-lg;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;
  }

  &__label {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }

  // --- Footer ---
  &__footer {
    padding: $spacing-base $spacing-lg;
    border-top: 1px solid $border-lighter;
  }

  &__apply-btn {
    width: 100%;
    height: 40px;
    font-size: 15px;
  }
}

// Slide transition
.popup-slide-enter-active,
.popup-slide-leave-active {
  transition: all 0.2s ease;
}

.popup-slide-enter-from,
.popup-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
