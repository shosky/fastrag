<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useSystemStore } from '@/stores/system'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as api from '@/api'

const router = useRouter()
const userStore = useUserStore()
const systemStore = useSystemStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 登录方式切换：'password' | 'qrcode'
const loginTab = ref<'password' | 'qrcode'>('password')

onMounted(async () => {
  try {
    await systemStore.loadConfig()
  } catch { /* 登录页加载系统配置是可选的，静默失败 */ }
})

// ==================== 账号密码登录 ====================

const loginForm = reactive({
  username: 'admin',
  password: 'admin123',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const success = await userStore.login(loginForm.username, loginForm.password)
    if (success) {
      ElMessage.success('登录成功')
      router.push('/home')
    } else {
      ElMessage.error('登录失败')
    }
  } finally {
    loading.value = false
  }
}

// ==================== 微信扫码登录 ====================

const qrScene = ref('')
const qrImageBase64 = ref('')
const qrStatus = ref<'loading' | 'waiting' | 'scanned' | 'expired' | 'error'>('loading')
const qrCountdown = ref(0)
let pollTimer: ReturnType<typeof setInterval> | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null

async function loadQrCode() {
  qrStatus.value = 'loading'
  qrImageBase64.value = ''
  stopPolling()

  try {
    const res: any = await api.getWechatQrScene()
    qrScene.value = res.scene
    qrImageBase64.value = res.qrImageBase64 || ''
    qrStatus.value = 'waiting'
    qrCountdown.value = res.expiresIn || 300

    // 启动倒计时
    countdownTimer = setInterval(() => {
      qrCountdown.value--
      if (qrCountdown.value <= 0) {
        qrStatus.value = 'expired'
        stopPolling()
      }
    }, 1000)

    // 启动轮询（每 2 秒）
    pollTimer = setInterval(pollStatus, 2000)
  } catch {
    qrStatus.value = 'error'
  }
}

async function pollStatus() {
  if (!qrScene.value) return
  try {
    const res: any = await api.pollWechatQrStatus(qrScene.value)
    if (res.status === 'expired') {
      qrStatus.value = 'expired'
      stopPolling()
    } else if (res.status === 'scanned' && res.token) {
      qrStatus.value = 'scanned'
      stopPolling()
      // 登录成功
      userStore.setToken(res.token)
      try {
        const info: any = await api.getUserInfo()
        if (info) userStore.setUserInfo(info)
      } catch { /* ignore */ }
      ElMessage.success('登录成功')
      router.push('/home')
    }
  } catch {
    // 轮询错误不影响主流程，继续等
  }
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}

// 切换到扫码 tab 时加载二维码
const prevTab = ref(loginTab.value)
watch(() => loginTab.value, (val) => {
  if (val === 'qrcode' && prevTab.value !== 'qrcode') {
    nextTick(() => loadQrCode())
  }
  prevTab.value = val
})

onUnmounted(() => stopPolling())

const formatCountdown = computed(() => {
  const min = Math.floor(qrCountdown.value / 60)
  const sec = qrCountdown.value % 60
  return `${min}:${sec.toString().padStart(2, '0')}`
})
</script>

<template>
  <div class="login-page">
    <!-- ==================== Left: Brand Panel ==================== -->
    <div class="login-brand">
      <div class="login-brand__glow login-brand__glow--1" />
      <div class="login-brand__glow login-brand__glow--2" />
      <div class="login-brand__grid" />

      <div class="login-brand__inner">
        <span class="login-brand__tag">✦ 企业级 RAG 平台</span>
        <h2 class="login-brand__title">连接知识，<br>释放 AI 的无限可能</h2>
        <p class="login-brand__desc">
          多模态知识处理 · 智能检索增强生成 · 知识图谱驱动，让企业知识资产真正流动起来。
        </p>
        <div class="login-brand__stats">
          <div class="login-brand__stat">
            <b>10M+</b><span>知识片段</span>
          </div>
          <div class="login-brand__stat">
            <b>99.9%</b><span>检索准确率</span>
          </div>
          <div class="login-brand__stat">
            <b>24/7</b><span>稳定服务</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== Right: Form Panel ==================== -->
    <div class="login-form-panel">
      <div class="login-card">
        <!-- Header -->
        <div class="login-header">
          <h1>{{ systemStore.systemName }}</h1>
          <p>{{ systemStore.slogan }}</p>
        </div>

        <!-- Tab Switch -->
        <div class="login-tabs">
          <button
            class="login-tabs__item"
            :class="{ 'login-tabs__item--active': loginTab === 'qrcode' }"
            @click="loginTab = 'qrcode'"
          >
            <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16">
              <path fill-rule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2.586a1 1 0 01-.293.707l-3.5 3.5a1 1 0 00-.293.707V16a1 1 0 01-1.414.832L8.12 14.83A1 1 0 018 14.16v-3.17a1 1 0 00-.293-.707l-3.5-3.5A1 1 0 014 8.586V4z" clip-rule="evenodd" />
            </svg>
            扫码登录
          </button>
          <button
            class="login-tabs__item"
            :class="{ 'login-tabs__item--active': loginTab === 'password' }"
            @click="loginTab = 'password'"
          >
            <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16">
              <path fill-rule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clip-rule="evenodd" />
            </svg>
            账号登录
          </button>
        </div>

        <!-- ====== 扫码登录 ====== -->
        <div v-show="loginTab === 'qrcode'" class="login-qrcode">
          <div class="login-qrcode__wrapper">
            <!-- Loading -->
            <div v-if="qrStatus === 'loading'" class="login-qrcode__placeholder">
              <el-icon class="is-loading" :size="28" color="#c0c4cc"><Loading /></el-icon>
              <span>二维码加载中...</span>
            </div>
            <!-- QR Code -->
            <img
              v-else-if="qrImageBase64 && (qrStatus === 'waiting' || qrStatus === 'scanned')"
              :src="'data:image/png;base64,' + qrImageBase64"
              class="login-qrcode__image"
              :class="{ 'login-qrcode__image--scanned': qrStatus === 'scanned' }"
              alt="微信小程序码"
            />
            <!-- Expired / Error / No QR -->
            <div v-else class="login-qrcode__placeholder">
              <template v-if="qrStatus === 'expired'">
                <svg viewBox="0 0 20 20" fill="currentColor" width="32" height="32" color="#c0c4cc">
                  <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clip-rule="evenodd" />
                </svg>
                <span>二维码已过期</span>
              </template>
              <template v-else>
                <svg viewBox="0 0 20 20" fill="currentColor" width="32" height="32" color="#909399">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd" />
                </svg>
                <span>请配置微信小程序后使用</span>
              </template>
              <button class="login-qrcode__refresh" @click="loadQrCode">刷新二维码</button>
            </div>
          </div>
          <div class="login-qrcode__tips">
            <template v-if="qrStatus === 'waiting'">
              <p class="login-qrcode__tip">请使用<strong>微信</strong>扫一扫</p>
              <p class="login-qrcode__tip">扫描小程序码登录</p>
            </template>
            <template v-else-if="qrStatus === 'scanned'">
              <p class="login-qrcode__tip login-qrcode__tip--success">扫码成功，正在登录...</p>
            </template>
            <template v-else>
              <p class="login-qrcode__tip">请使用<strong>微信</strong>扫一扫</p>
            </template>
          </div>
          <p v-if="qrStatus === 'waiting'" class="login-qrcode__countdown">
            二维码有效期：{{ formatCountdown }}
          </p>
        </div>

        <!-- ====== 账号密码登录 ====== -->
        <div v-show="loginTab === 'password'">
          <el-form
            ref="formRef"
            :model="loginForm"
            :rules="rules"
            size="large"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                prefix-icon="User"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <el-form-item>
              <button
                type="button"
                class="login-btn"
                :disabled="loading"
                @click="handleLogin"
              >
                <span v-if="!loading">登 录</span>
                <span v-else class="login-btn__loading">
                  <i class="el-icon is-loading" />
                </span>
              </button>
            </el-form-item>
          </el-form>
        </div>

        <!-- Footer links (only for password tab) -->
        <div v-show="loginTab === 'password'" class="login-footer">
          <a class="login-footer__link" href="javascript:void(0)" @click="router.push('/forgot-password')">忘记密码？</a>
          <a class="login-footer__link" href="javascript:void(0)" @click="router.push('/register')">注册账号</a>
        </div>

        <!-- Copyright -->
        <div class="login-copyright">
          <p>{{ systemStore.copyright || '© ' + new Date().getFullYear() + ' AIS. All rights reserved.' }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

// ============================================================
// Design Tokens (login-specific)
// ============================================================
$login-brand-blue: #0066ff;
$login-brand-blue-dark: #0052cc;
$login-brand-blue-light: #e6f0ff;

$login-heading-color: #1f2329;
$login-subtitle-color: #646a73;
$login-input-bg: #f5f6f7;
$login-input-bg-focus: #ffffff;

$brand-bg-start: #0b1437;
$brand-bg-mid: #1a2460;
$brand-bg-end: #0e3a6e;

// ============================================================
// Page Layout
// ============================================================
.login-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 100vh;
  overflow: hidden;
}

// ============================================================
// Left Brand Panel (unchanged)
// ============================================================
.login-brand {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 55%;
  min-width: 420px;
  padding: 48px 56px;
  background: linear-gradient(160deg, $brand-bg-start 0%, $brand-bg-mid 50%, $brand-bg-end 100%);
  overflow: hidden;

  &__glow {
    position: absolute;
    border-radius: 50%;
    pointer-events: none;
    &--1 { top: -10%; right: -5%; width: 500px; height: 500px; background: radial-gradient(circle, rgba(99, 102, 241, 0.18) 0%, transparent 70%); }
    &--2 { bottom: -15%; left: -8%; width: 450px; height: 450px; background: radial-gradient(circle, rgba(14, 165, 233, 0.15) 0%, transparent 70%); }
  }

  &__grid {
    position: absolute; inset: 0;
    background-image: linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
    background-size: 60px 60px;
    pointer-events: none;
  }

  &__inner {
    position: relative; z-index: 2;
    display: flex; flex-direction: column; align-items: flex-start;
    max-width: 480px; width: 100%;
  }

  &__tag {
    display: inline-flex; align-items: center;
    width: fit-content; padding: 6px 16px; margin-bottom: 28px;
    border-radius: 999px; font-size: 13px; letter-spacing: 0.5px;
    color: rgba(255, 255, 255, 0.85);
    background: rgba(255, 255, 255, 0.10); border: 1px solid rgba(255, 255, 255, 0.14);
    backdrop-filter: blur(4px); -webkit-backdrop-filter: blur(4px);
  }

  &__title { font-size: 34px; font-weight: 800; line-height: 1.3; color: #ffffff; letter-spacing: 0.5px; }

  &__desc { margin-top: 18px; font-size: 15px; line-height: 1.8; color: rgba(255, 255, 255, 0.70); }

  &__stats { display: flex; gap: 40px; margin-top: 48px; }

  &__stat {
    b { display: block; font-size: 26px; font-weight: 700; color: #ffffff; }
    span { display: block; margin-top: 4px; font-size: 13px; color: rgba(255, 255, 255, 0.60); }
  }
}

// ============================================================
// Right Form Panel
// ============================================================
.login-form-panel {
  flex: 1; display: flex; align-items: center; justify-content: center;
  background: #fafbfc;
}

// ============================================================
// Login Card
// ============================================================
.login-card {
  width: 420px; max-width: 90%; padding: 48px 40px 32px;
  background: #ffffff; border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.05);
}

// ============================================================
// Header
// ============================================================
.login-header { margin-bottom: 28px; h1 { font-size: 26px; font-weight: 700; color: $login-heading-color; letter-spacing: 0.5px; margin-bottom: 6px; } p { font-size: 14px; color: $login-subtitle-color; } }

// ============================================================
// Tab Switch
// ============================================================
.login-tabs {
  display: flex; gap: 4px;
  padding: 4px; margin-bottom: 28px;
 background: $login-input-bg; border-radius: 10px;

  &__item {
    flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
    padding: 10px 0; border: none; border-radius: 8px;
    font-size: 14px; font-weight: 500; color: $login-subtitle-color;
    background: transparent; cursor: pointer;
    transition: all 0.25s ease;

    &:hover { color: $login-heading-color; }

    &--active {
      color: $login-brand-blue; background: #ffffff;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
    }
  }
}

// ============================================================
// QR Code Section
// ============================================================
.login-qrcode {
  display: flex; flex-direction: column; align-items: center;

  &__wrapper {
    width: 200px; height: 200px; border-radius: 12px;
    border: 2px solid #ebeef5; overflow: hidden;
    display: flex; align-items: center; justify-content: center;
    background: #fafafa;
  }

  &__image {
    width: 100%; height: 100%; object-fit: contain;
    transition: opacity 0.3s ease;

    &--scanned { opacity: 0.4; }
  }

  &__placeholder {
    display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px;
    width: 100%; height: 100%;
    span { font-size: 13px; color: $text-secondary; }
  }

  &__refresh {
    margin-top: 4px; padding: 4px 14px; border: 1px solid $border-base; border-radius: 6px;
    font-size: 12px; color: $login-brand-blue; background: transparent; cursor: pointer;
    transition: all 0.2s ease;
    &:hover { background: $login-brand-blue-light; border-color: $login-brand-blue; }
  }

  &__tips { margin-top: 20px; text-align: center; }
  &__tip { font-size: 13px; color: $login-subtitle-color; line-height: 1.8; strong { color: $login-heading-color; }
    &--success { color: #07c160; font-weight: 500; }
  }
  &__countdown { margin-top: 8px; font-size: 12px; color: $text-disabled; }
}

// ============================================================
// Override Element Plus Input Styles
// ============================================================
.login-card {
  :deep(.el-input) {
    .el-input__wrapper { height: 46px; padding: 0 15px; background-color: $login-input-bg; border: 1.5px solid transparent; border-radius: 10px; box-shadow: none; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      &:hover { background-color: $login-input-bg-focus; border-color: $border-base; }
      &.is-focus { background-color: $login-input-bg-focus; border-color: $login-brand-blue; box-shadow: 0 0 0 3px rgba($login-brand-blue, 0.12); }
    }
    .el-input__prefix .el-icon, .el-input__prefix-inner > svg { color: $text-placeholder; }
    .el-input__inner { font-size: 14px; color: $login-heading-color; &::placeholder { color: $text-placeholder; } }
  }
  :deep(.el-form-item) { margin-bottom: 20px; }
}

// ============================================================
// Login Button
// ============================================================
.login-btn {
  display: flex; align-items: center; justify-content: center;
  width: 100%; height: 46px; font-size: 15px; font-weight: 600; letter-spacing: 1px;
  color: #ffffff; background: linear-gradient(135deg, $login-brand-blue 0%, #3385ff 100%);
  border: none; border-radius: 10px; cursor: pointer;
  box-shadow: 0 4px 14px rgba($login-brand-blue, 0.3); transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  &:hover { background: linear-gradient(135deg, $login-brand-blue-dark 0%, $login-brand-blue 100%); box-shadow: 0 6px 20px rgba($login-brand-blue, 0.4); }
  &:active { transform: scale(0.98); box-shadow: 0 2px 10px rgba($login-brand-blue, 0.3); }
  &:disabled { opacity: 0.7; cursor: not-allowed; }
  &__loading { display: inline-flex; align-items: center; justify-content: center; i { display: inline-block; width: 18px; height: 18px; border: 2px solid rgba(255, 255, 255, 0.3); border-top-color: #ffffff; border-radius: 50%; animation: login-spin 0.6s linear infinite; } }
}

@keyframes login-spin { to { transform: rotate(360deg); } }

// ============================================================
// Footer Links
// ============================================================
.login-footer { display: flex; justify-content: flex-end; gap: 20px; margin-top: 8px;
  &__link { font-size: 13px; color: $text-placeholder; text-decoration: none; transition: color 0.25s ease; &:hover { color: $login-brand-blue; } }
}

// ============================================================
// Copyright
// ============================================================
.login-copyright { margin-top: 40px; text-align: center; p { font-size: 12px; color: $text-disabled; } }

// ============================================================
// Responsive Design
// ============================================================
@media screen and (max-width: 1024px) {
  .login-brand { width: 45%; min-width: 340px; padding: 32px; &__inner { max-width: 340px; } &__title { font-size: 26px; } &__desc { font-size: 14px; } &__stats { gap: 24px; margin-top: 32px; } &__stat b { font-size: 22px; } }
}

@media screen and (max-width: 768px) {
  .login-page { flex-direction: column; }
  .login-brand { display: none; }
  .login-form-panel { flex: 1; background: linear-gradient(180deg, #f0f4ff 0%, #fafbfc 100%); padding: 24px; }
  .login-card { padding: 36px 28px 24px; box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08); }
  .login-header { margin-bottom: 20px; h1 { font-size: 22px; } }
}
</style>
