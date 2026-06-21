<script setup lang="ts">
import { VideoPlay, VideoPause, Mute, Microphone, FullScreen } from '@element-plus/icons-vue'

const props = defineProps<{
  src: string
  title: string
}>()

// ---------- Video state ----------
const videoRef = ref<HTMLVideoElement | null>(null)
const playerRef = ref<HTMLDivElement | null>(null)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(0.75)
const isMuted = ref(false)
const previousVolume = ref(0.75)
const activeTab = ref('keyframes')
const isFullscreen = ref(false)

// ---------- Mock keyframe data ----------
const keyframes = ref([
  { time: 5, thumbnail: '', description: '产品架构介绍' },
  { time: 30, thumbnail: '', description: '数据库设计' },
  { time: 75, thumbnail: '', description: 'API接口说明' },
  { time: 120, thumbnail: '', description: '部署流程' },
])

// ---------- Mock ASR transcripts ----------
const transcripts = ref([
  { time: 0, text: '今天我们来讲一下系统架构。' },
  { time: 15, text: '首先是前端部分，使用 Vue 3 框架。' },
  { time: 32, text: '后端采用 Python FastAPI。' },
  { time: 48, text: '数据库使用 PostgreSQL。' },
  { time: 65, text: '部署方面使用 Docker 容器化。' },
])

// ---------- Computed ----------
const formattedCurrentTime = computed(() => formatTime(currentTime.value))
const formattedDuration = computed(() => formatTime(duration.value))

const activeTranscriptIndex = computed(() => {
  let activeIdx = -1
  for (let i = transcripts.value.length - 1; i >= 0; i--) {
    if (currentTime.value >= transcripts.value[i].time) {
      activeIdx = i
      break
    }
  }
  return activeIdx
})

const activeKeyframeIndex = computed(() => {
  let activeIdx = -1
  for (let i = keyframes.value.length - 1; i >= 0; i--) {
    if (currentTime.value >= keyframes.value[i].time) {
      activeIdx = i
      break
    }
  }
  return activeIdx
})

// Build a merged timeline from keyframes and transcripts
const mergedTimeline = computed(() => {
  const events: { time: number; type: 'keyframe' | 'transcript'; data: any }[] = []
  for (const kf of keyframes.value) {
    events.push({ time: kf.time, type: 'keyframe', data: kf })
  }
  for (const tr of transcripts.value) {
    events.push({ time: tr.time, type: 'transcript', data: tr })
  }
  events.sort((a, b) => a.time - b.time)
  return events
})

// ---------- Methods ----------
function formatTime(seconds: number): string {
  if (!isFinite(seconds) || seconds < 0) return '00:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

function togglePlay() {
  const video = videoRef.value
  if (!video) return
  if (isPlaying.value) {
    video.pause()
  } else {
    video.play()
  }
}

function onTimeUpdate() {
  if (videoRef.value) {
    currentTime.value = videoRef.value.currentTime
  }
}

function onLoadedMetadata() {
  if (videoRef.value) {
    duration.value = videoRef.value.duration
  }
}

function onPlay() {
  isPlaying.value = true
}

function onPause() {
  isPlaying.value = false
}

function onEnded() {
  isPlaying.value = false
  currentTime.value = 0
}

function onSeek(value: number | number[]) {
  const time = Array.isArray(value) ? value[0] : value
  if (videoRef.value) {
    videoRef.value.currentTime = time
    currentTime.value = time
  }
}

function toggleMute() {
  const video = videoRef.value
  if (!video) return
  if (isMuted.value) {
    isMuted.value = false
    volume.value = previousVolume.value || 0.75
    video.volume = volume.value
  } else {
    previousVolume.value = volume.value
    isMuted.value = true
    volume.value = 0
    video.volume = 0
  }
}

function onVolumeChange(value: number | number[]) {
  const vol = Array.isArray(value) ? value[0] : value
  if (videoRef.value) {
    volume.value = vol
    videoRef.value.volume = vol
    isMuted.value = vol === 0
  }
}

function seekTo(time: number) {
  if (videoRef.value) {
    videoRef.value.currentTime = time
    currentTime.value = time
    if (!isPlaying.value) {
      videoRef.value.play()
    }
  }
}

function toggleFullscreen() {
  const el = playerRef.value
  if (!el) return
  if (!document.fullscreenElement) {
    el.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

// Watch src changes to reset state
watch(
  () => props.src,
  () => {
    isPlaying.value = false
    currentTime.value = 0
    duration.value = 0
  },
)

// Listen for fullscreen changes
onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<template>
  <div ref="playerRef" class="video-player">
    <!-- Title -->
    <div v-if="title" class="video-player__title">{{ title }}</div>

    <!-- Video container -->
    <div class="video-player__video-wrapper">
      <video
        ref="videoRef"
        :src="src"
        preload="metadata"
        class="video-player__video"
        @timeupdate="onTimeUpdate"
        @loadedmetadata="onLoadedMetadata"
        @play="onPlay"
        @pause="onPause"
        @ended="onEnded"
        @click="togglePlay"
      />

      <!-- Player controls bar -->
      <div class="video-player__controls">
        <!-- Play / Pause button -->
        <el-button
          circle
          :icon="isPlaying ? VideoPause : VideoPlay"
          class="video-player__play-btn"
          @click.stop="togglePlay"
        />

        <!-- Time display -->
        <span class="video-player__time">
          {{ formattedCurrentTime }} / {{ formattedDuration }}
        </span>

        <!-- Progress slider -->
        <el-slider
          :model-value="currentTime"
          :max="duration || 0"
          :step="0.1"
          :show-tooltip="false"
          class="video-player__progress"
          @input="onSeek"
        />

        <!-- Volume control -->
        <div class="video-player__volume">
          <el-button
            circle
            :icon="isMuted || volume === 0 ? Mute : Microphone"
            size="small"
            class="video-player__mute-btn"
            @click.stop="toggleMute"
          />
          <el-slider
            :model-value="volume"
            :min="0"
            :max="1"
            :step="0.01"
            :show-tooltip="false"
            class="video-player__volume-slider"
            @input="onVolumeChange"
          />
        </div>

        <!-- Fullscreen button -->
        <el-button
          circle
          :icon="FullScreen"
          class="video-player__fullscreen-btn"
          @click.stop="toggleFullscreen"
        />
      </div>
    </div>

    <!-- Content tabs -->
    <div class="video-player__tabs">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- Keyframes tab -->
        <el-tab-pane label="关键帧" name="keyframes">
          <el-scrollbar max-height="280px">
            <div class="video-player__keyframes-grid">
              <div
                v-for="(kf, index) in keyframes"
                :key="index"
                class="video-player__keyframe-card"
                :class="{ 'is-active': index === activeKeyframeIndex }"
                @click="seekTo(kf.time)"
              >
                <div class="video-player__keyframe-thumbnail">
                  <div v-if="!kf.thumbnail" class="video-player__keyframe-placeholder">
                    <el-icon :size="28"><VideoPlay /></el-icon>
                  </div>
                  <img v-else :src="kf.thumbnail" alt="keyframe" />
                  <el-tag
                    size="small"
                    class="video-player__keyframe-time-badge"
                    type="info"
                  >
                    {{ formatTime(kf.time) }}
                  </el-tag>
                </div>
                <div class="video-player__keyframe-desc">{{ kf.description }}</div>
              </div>
            </div>
          </el-scrollbar>
        </el-tab-pane>

        <!-- ASR transcript tab -->
        <el-tab-pane label="ASR 转写" name="transcript">
          <el-scrollbar max-height="280px">
            <div class="video-player__transcript-list">
              <div
                v-for="(item, index) in transcripts"
                :key="index"
                class="video-player__transcript-item"
                :class="{ 'is-active': index === activeTranscriptIndex }"
                @click="seekTo(item.time)"
              >
                <el-tag size="small" type="info" class="video-player__transcript-time">
                  {{ formatTime(item.time) }}
                </el-tag>
                <span class="video-player__transcript-text">{{ item.text }}</span>
              </div>
            </div>
          </el-scrollbar>
        </el-tab-pane>

        <!-- Merged view tab -->
        <el-tab-pane label="合并视图" name="merged">
          <el-scrollbar max-height="280px">
            <div class="video-player__merged-list">
              <div
                v-for="(event, index) in mergedTimeline"
                :key="index"
                class="video-player__merged-item"
                :class="{
                  'is-keyframe': event.type === 'keyframe',
                  'is-transcript': event.type === 'transcript',
                }"
                @click="seekTo(event.time)"
              >
                <el-tag
                  size="small"
                  :type="event.type === 'keyframe' ? 'warning' : 'info'"
                  class="video-player__merged-time"
                >
                  {{ formatTime(event.time) }}
                </el-tag>
                <el-tag
                  v-if="event.type === 'keyframe'"
                  size="small"
                  type="warning"
                  effect="plain"
                  class="video-player__merged-type"
                >
                  关键帧
                </el-tag>
                <span
                  v-if="event.type === 'keyframe'"
                  class="video-player__merged-text"
                >
                  {{ event.data.description }}
                </span>
                <span v-else class="video-player__merged-text">
                  {{ event.data.text }}
                </span>
              </div>
            </div>
          </el-scrollbar>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.video-player {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
  width: 100%;

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    padding: 0 $spacing-xs;
  }

  // --- Video wrapper (contains video + controls overlay) ---
  &__video-wrapper {
    position: relative;
    width: 100%;
    max-height: 50vh;
    background: #000;
    border-radius: $radius-lg;
    overflow: hidden;
  }

  &__video {
    display: block;
    width: 100%;
    max-height: 50vh;
    object-fit: contain;
    cursor: pointer;
  }

  // --- Controls bar (dark background overlay) ---
  &__controls {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    padding: $spacing-sm $spacing-md;
    background: #1a1a1a;
  }

  &__play-btn {
    flex-shrink: 0;

    --el-button-bg-color: transparent;
    --el-button-border-color: transparent;
    --el-button-hover-bg-color: rgba(255, 255, 255, 0.15);
    --el-button-hover-border-color: transparent;
    --el-button-text-color: #ffffff;
  }

  &__time {
    flex-shrink: 0;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
    font-size: 13px;
    color: #ffffff;
    min-width: 96px;
    text-align: center;
  }

  &__progress {
    flex: 1;
    min-width: 0;

    :deep(.el-slider__runway) {
      height: 4px;
      background-color: rgba(255, 255, 255, 0.2);
    }

    :deep(.el-slider__bar) {
      height: 4px;
      background-color: $color-primary;
    }

    :deep(.el-slider__button-wrapper) {
      top: -16px;
    }

    :deep(.el-slider__button) {
      width: 12px;
      height: 12px;
      border: 2px solid $color-primary;
    }
  }

  // --- Volume control ---
  &__volume {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    flex-shrink: 0;
  }

  &__mute-btn {
    flex-shrink: 0;

    --el-button-bg-color: transparent;
    --el-button-border-color: transparent;
    --el-button-hover-bg-color: rgba(255, 255, 255, 0.15);
    --el-button-hover-border-color: transparent;
    --el-button-text-color: #ffffff;
  }

  &__volume-slider {
    width: 80px;

    :deep(.el-slider__runway) {
      height: 4px;
      background-color: rgba(255, 255, 255, 0.2);
    }

    :deep(.el-slider__bar) {
      height: 4px;
      background-color: $color-primary;
    }

    :deep(.el-slider__button-wrapper) {
      top: -16px;
    }

    :deep(.el-slider__button) {
      width: 10px;
      height: 10px;
      border: 2px solid $color-primary;
    }
  }

  &__fullscreen-btn {
    flex-shrink: 0;

    --el-button-bg-color: transparent;
    --el-button-border-color: transparent;
    --el-button-hover-bg-color: rgba(255, 255, 255, 0.15);
    --el-button-hover-border-color: transparent;
    --el-button-text-color: #ffffff;
  }

  // --- Content tabs ---
  &__tabs {
    :deep(.el-tabs--border-card) {
      border-radius: $radius-lg;
      overflow: hidden;
    }
  }

  // --- Keyframes grid ---
  &__keyframes-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: $spacing-md;
    padding: $spacing-base;
  }

  &__keyframe-card {
    cursor: pointer;
    border: 2px solid transparent;
    border-radius: $radius-base;
    transition: border-color 0.2s, box-shadow 0.2s;
    overflow: hidden;
    background: $bg-white;

    &:hover {
      border-color: $color-primary;
      box-shadow: $shadow-base;
    }

    &.is-active {
      border-color: $color-primary;
      box-shadow: 0 0 0 1px $color-primary;
    }
  }

  &__keyframe-thumbnail {
    position: relative;
    width: 100%;
    aspect-ratio: 16 / 9;
    background: #f0f0f0;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  &__keyframe-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    color: $text-placeholder;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

    .el-icon {
      color: #ffffff;
    }
  }

  &__keyframe-time-badge {
    position: absolute;
    bottom: $spacing-xs;
    right: $spacing-xs;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
  }

  &__keyframe-desc {
    padding: $spacing-sm $spacing-md;
    font-size: 13px;
    color: $text-regular;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  // --- Transcript list ---
  &__transcript-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: $spacing-base;
  }

  &__transcript-item {
    display: flex;
    align-items: flex-start;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-md;
    border-radius: $radius-sm;
    cursor: pointer;
    transition: background-color 0.2s;

    &:hover {
      background-color: $bg-hover;
    }

    &.is-active {
      background-color: $bg-active;

      .video-player__transcript-text {
        color: $color-primary;
        font-weight: 500;
      }
    }
  }

  &__transcript-time {
    flex-shrink: 0;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
  }

  &__transcript-text {
    font-size: 13px;
    line-height: 1.6;
    color: $text-regular;
  }

  // --- Merged view ---
  &__merged-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: $spacing-base;
  }

  &__merged-item {
    display: flex;
    align-items: flex-start;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-md;
    border-radius: $radius-sm;
    cursor: pointer;
    transition: background-color 0.2s;

    &:hover {
      background-color: $bg-hover;
    }

    &.is-keyframe {
      background-color: #fffbe6;

      &:hover {
        background-color: #fff7cc;
      }
    }
  }

  &__merged-time {
    flex-shrink: 0;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
  }

  &__merged-type {
    flex-shrink: 0;
  }

  &__merged-text {
    font-size: 13px;
    line-height: 1.6;
    color: $text-regular;
  }
}
</style>
