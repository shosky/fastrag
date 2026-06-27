<script setup lang="ts">
import { VideoPlay, VideoPause, Mute, Microphone } from '@element-plus/icons-vue'

const props = defineProps<{
  src: string
  title: string
  transcripts?: Array<{ time: number; text: string }>
}>()

// ---------- Audio state ----------
const audioRef = ref<HTMLAudioElement | null>(null)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(0.75)
const isMuted = ref(false)
const previousVolume = ref(0.75)

// ---------- ASR transcripts (from prop or mock) ----------
const transcripts = computed(() => props.transcripts && props.transcripts.length > 0
  ? props.transcripts
  : [
      { time: 0, text: '暂无 ASR 转写数据，请先处理文件。' },
    ]
)

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

// ---------- Methods ----------
function formatTime(seconds: number): string {
  if (!isFinite(seconds) || seconds < 0) return '00:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

function togglePlay() {
  const audio = audioRef.value
  if (!audio) return
  if (isPlaying.value) {
    audio.pause()
  } else {
    audio.play()
  }
}

function onTimeUpdate() {
  if (audioRef.value) {
    currentTime.value = audioRef.value.currentTime
  }
}

function onLoadedMetadata() {
  if (audioRef.value) {
    duration.value = audioRef.value.duration
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
  if (audioRef.value) {
    audioRef.value.currentTime = time
    currentTime.value = time
  }
}

function toggleMute() {
  const audio = audioRef.value
  if (!audio) return
  if (isMuted.value) {
    isMuted.value = false
    volume.value = previousVolume.value || 0.75
    audio.volume = volume.value
  } else {
    previousVolume.value = volume.value
    isMuted.value = true
    volume.value = 0
    audio.volume = 0
  }
}

function onVolumeChange(value: number | number[]) {
  const vol = Array.isArray(value) ? value[0] : value
  if (audioRef.value) {
    volume.value = vol
    audioRef.value.volume = vol
    isMuted.value = vol === 0
  }
}

function jumpToTranscript(time: number) {
  if (audioRef.value) {
    audioRef.value.currentTime = time
    currentTime.value = time
    if (!isPlaying.value) {
      audioRef.value.play()
    }
  }
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
</script>

<template>
  <div class="audio-player">
    <!-- Title -->
    <div v-if="title" class="audio-player__title">{{ title }}</div>

    <!-- Hidden audio element -->
    <audio
      ref="audioRef"
      :src="src"
      preload="metadata"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMetadata"
      @play="onPlay"
      @pause="onPause"
      @ended="onEnded"
    />

    <!-- Player controls bar -->
    <div class="audio-player__controls">
      <!-- Play / Pause button -->
      <el-button
        circle
        :icon="isPlaying ? VideoPause : VideoPlay"
        class="audio-player__play-btn"
        @click="togglePlay"
      />

      <!-- Time display -->
      <span class="audio-player__time">
        {{ formattedCurrentTime }} / {{ formattedDuration }}
      </span>

      <!-- Progress slider -->
      <el-slider
        :model-value="currentTime"
        :max="duration || 0"
        :step="0.1"
        :show-tooltip="false"
        class="audio-player__progress"
        @input="onSeek"
      />

      <!-- Volume control -->
      <div class="audio-player__volume">
        <el-button
          circle
          :icon="isMuted || volume === 0 ? Mute : Microphone"
          size="small"
          class="audio-player__mute-btn"
          @click="toggleMute"
        />
        <el-slider
          :model-value="volume"
          :min="0"
          :max="1"
          :step="0.01"
          :show-tooltip="false"
          class="audio-player__volume-slider"
          @input="onVolumeChange"
        />
      </div>
    </div>

    <!-- ASR transcript section -->
    <div class="audio-player__transcript">
      <div class="audio-player__transcript-title">ASR 转写结果</div>
      <el-scrollbar max-height="240px">
        <div class="audio-player__transcript-list">
          <div
            v-for="(item, index) in transcripts"
            :key="index"
            class="audio-player__transcript-item"
            :class="{ 'is-active': index === activeTranscriptIndex }"
            @click="jumpToTranscript(item.time)"
          >
            <el-tag size="small" type="info" class="audio-player__transcript-time">
              {{ formatTime(item.time) }}
            </el-tag>
            <span class="audio-player__transcript-text">{{ item.text }}</span>
          </div>
        </div>
      </el-scrollbar>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.audio-player {
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

  // --- Controls bar ---
  &__controls {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    padding: $spacing-md $spacing-base;
    background: $bg-white;
    border-radius: $radius-lg;
    box-shadow: $shadow-base;
  }

  &__play-btn {
    flex-shrink: 0;
  }

  &__time {
    flex-shrink: 0;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
    font-size: 13px;
    color: $text-secondary;
    min-width: 96px;
    text-align: center;
  }

  &__progress {
    flex: 1;
    min-width: 0;

    :deep(.el-slider__runway) {
      height: 4px;
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
  }

  &__volume-slider {
    width: 80px;

    :deep(.el-slider__runway) {
      height: 4px;
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

  // --- ASR Transcript section ---
  &__transcript {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
    padding: $spacing-base;
    background: $bg-white;
    border-radius: $radius-lg;
    box-shadow: $shadow-sm;
  }

  &__transcript-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }

  &__transcript-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
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

      .audio-player__transcript-text {
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
}
</style>
