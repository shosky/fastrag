<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSystemStore } from '@/stores/system'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as api from '@/api'

const router = useRouter()
const systemStore = useSystemStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  try {
    await systemStore.loadConfig()
  } catch { /* 静默失败 */ }
})

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  code: '',
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度为3-64个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度为6-64个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
  ],
}

async function handleSendCode() {
  if (!registerForm.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  // 简单校验邮箱格式
  const emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailReg.test(registerForm.email)) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }

  try {
    await api.sendCode(registerForm.email, 'register')
    ElMessage.success('验证码已发送')
    startCountdown()
  } catch {
    // 错误已由 request 拦截器处理
  }
}

function startCountdown() {
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await api.register({
      username: registerForm.username,
      email: registerForm.email,
      password: registerForm.password,
      code: registerForm.code,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    // 错误已由 request 拦截器处理
  } finally {
    loading.value = false
  }
}

function goToLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="register-page">
    <!-- ==================== Left: Brand Panel ==================== -->
    <div class="login-brand">
      <div class="login-brand__glow login-brand__glow--1" />
      <div class="login-brand__glow login-brand__glow--2" />
      <div class="login-brand__grid" />

      <div class="login-brand__inner">
        <div class="login-brand__illustration">
          <svg viewBox="0 0 520 280" fill="none" xmlns="http://www.w3.org/2000/svg">
            <line x1="260" y1="110" x2="120" y2="50" stroke="rgba(255,255,255,0.18)" stroke-width="1.5" />
            <line x1="260" y1="110" x2="400" y2="45" stroke="rgba(255,255,255,0.18)" stroke-width="1.5" />
            <line x1="260" y1="110" x2="100" y2="180" stroke="rgba(255,255,255,0.15)" stroke-width="1.5" />
            <line x1="260" y1="110" x2="420" y2="175" stroke="rgba(255,255,255,0.15)" stroke-width="1.5" />
            <line x1="260" y1="110" x2="260" y2="240" stroke="rgba(255,255,255,0.20)" stroke-width="2" />
            <line x1="120" y1="50" x2="55" y2="120" stroke="rgba(255,255,255,0.10)" stroke-width="1" />
            <line x1="120" y1="50" x2="180" y2="15" stroke="rgba(255,255,255,0.10)" stroke-width="1" />
            <line x1="400" y1="45" x2="465" y2="110" stroke="rgba(255,255,255,0.10)" stroke-width="1" />
            <line x1="400" y1="45" x2="345" y2="10" stroke="rgba(255,255,255,0.10)" stroke-width="1" />
            <line x1="100" y1="180" x2="45" y2="245" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
            <line x1="100" y1="180" x2="165" y2="250" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
            <line x1="420" y1="175" x2="475" y2="245" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
            <line x1="420" y1="175" x2="355" y2="250" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
            <line x1="260" y1="240" x2="165" y2="250" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
            <line x1="260" y1="240" x2="355" y2="250" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
            <line x1="120" y1="50" x2="100" y2="180" stroke="rgba(255,255,255,0.06)" stroke-width="1" stroke-dasharray="4 4" />
            <line x1="400" y1="45" x2="420" y2="175" stroke="rgba(255,255,255,0.06)" stroke-width="1" stroke-dasharray="4 4" />
            <circle cx="260" cy="110" r="22" fill="rgba(99,180,255,0.12)" stroke="rgba(99,180,255,0.4)" stroke-width="1">
              <animate attributeName="r" values="22;70;22" dur="4s" repeatCount="indefinite" />
            </circle>
            <circle cx="260" cy="110" r="14" fill="rgba(99,180,255,0.18)" stroke="rgba(99,180,255,0.6)" stroke-width="1.5">
              <animate attributeName="r" values="14;38;14" dur="4s" begin="0.5s" repeatCount="indefinite" />
            </circle>
            <circle cx="260" cy="110" r="6" fill="#63b4ff" />
            <circle cx="120" cy="50" r="12" fill="rgba(130,120,255,0.15)" stroke="rgba(130,120,255,0.4)" stroke-width="1">
              <animate attributeName="r" values="12;36;12" dur="5s" begin="0.3s" repeatCount="indefinite" />
            </circle>
            <circle cx="120" cy="50" r="4.5" fill="#8278ff" />
            <circle cx="400" cy="45" r="12" fill="rgba(130,120,255,0.15)" stroke="rgba(130,120,255,0.4)" stroke-width="1">
              <animate attributeName="r" values="12;36;12" dur="5s" begin="1s" repeatCount="indefinite" />
            </circle>
            <circle cx="400" cy="45" r="4.5" fill="#8278ff" />
            <circle cx="100" cy="180" r="10" fill="rgba(99,180,255,0.12)" stroke="rgba(99,180,255,0.35)" stroke-width="1" />
            <circle cx="100" cy="180" r="3.5" fill="#63b4ff" />
            <circle cx="420" cy="175" r="10" fill="rgba(99,180,255,0.12)" stroke="rgba(99,180,255,0.35)" stroke-width="1" />
            <circle cx="420" cy="175" r="3.5" fill="#63b4ff" />
            <circle cx="260" cy="240" r="9" fill="rgba(130,120,255,0.12)" stroke="rgba(130,120,255,0.35)" stroke-width="1" />
            <circle cx="260" cy="240" r="3" fill="#8278ff" />
            <circle cx="55" cy="120" r="6" fill="rgba(130,120,255,0.10)" stroke="rgba(130,120,255,0.25)" stroke-width="1" />
            <circle cx="55" cy="120" r="2" fill="#8278ff" />
            <circle cx="180" cy="15" r="5" fill="rgba(99,180,255,0.08)" stroke="rgba(99,180,255,0.2)" stroke-width="1" />
            <circle cx="180" cy="15" r="1.5" fill="#63b4ff" />
            <circle cx="465" cy="110" r="6" fill="rgba(130,120,255,0.10)" stroke="rgba(130,120,255,0.25)" stroke-width="1" />
            <circle cx="465" cy="110" r="2" fill="#8278ff" />
            <circle cx="345" cy="10" r="5" fill="rgba(99,180,255,0.08)" stroke="rgba(99,180,255,0.2)" stroke-width="1" />
            <circle cx="345" cy="10" r="1.5" fill="#63b4ff" />
          </svg>
        </div>
        <div style="text-align: center;">
          <h2 style="color: rgba(255,255,255,0.92); font-size: 22px; font-weight: 600; margin-bottom: 8px;">知识驱动智能</h2>
          <p style="color: rgba(255,255,255,0.6); font-size: 14px;">注册即可体验多模态知识处理、RAG 智能检索与 LLM 知识图谱</p>
        </div>
      </div>
    </div>

    <!-- ==================== Right: Form Panel ==================== -->
    <div class="login-form-panel">
      <div class="login-card">
        <!-- Header -->
        <div class="login-header">
          <h1>注册账号</h1>
          <p>{{ systemStore.systemName }}</p>
        </div>

        <!-- Form -->
        <el-form
          ref="formRef"
          :model="registerForm"
          :rules="rules"
          size="large"
          @keyup.enter="handleRegister"
        >
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              placeholder="请输入用户名"
              prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="email">
            <el-input
              v-model="registerForm.email"
              placeholder="请输入邮箱"
              prefix-icon="Message"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请确认密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="code">
            <div style="display: flex; gap: 12px; width: 100%;">
              <el-input
                v-model="registerForm.code"
                placeholder="请输入邮箱验证码"
                prefix-icon="Key"
                style="flex: 1;"
              />
              <button
                type="button"
                class="code-btn"
                :disabled="countdown > 0"
                @click="handleSendCode"
              >
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </button>
            </div>
          </el-form-item>
          <el-form-item>
            <button
              type="button"
              class="login-btn"
              :disabled="loading"
              @click="handleRegister"
            >
              <span v-if="!loading">注 册</span>
              <span v-else class="login-btn__loading">
                <i class="el-icon is-loading" />
              </span>
            </button>
          </el-form-item>
        </el-form>

        <!-- Footer links -->
        <div class="login-footer">
          <a class="login-footer__link" href="javascript:void(0)" @click="goToLogin">返回登录</a>
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

$login-brand-blue: #0066ff;
$login-brand-blue-dark: #0052cc;
$login-heading-color: #1f2329;
$login-subtitle-color: #646a73;
$login-input-bg: #f5f6f7;
$login-input-bg-focus: #ffffff;

$brand-bg-start: #0b1437;
$brand-bg-mid: #1a2460;
$brand-bg-end: #0e3a6e;

.register-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 100vh;
  overflow: hidden;
}

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

    &--1 {
      top: -10%;
      right: -5%;
      width: 500px;
      height: 500px;
      background: radial-gradient(circle, rgba(99, 102, 241, 0.18) 0%, transparent 70%);
    }

    &--2 {
      bottom: -15%;
      left: -8%;
      width: 450px;
      height: 450px;
      background: radial-gradient(circle, rgba(14, 165, 233, 0.15) 0%, transparent 70%);
    }
  }

  &__grid {
    position: absolute;
    inset: 0;
    background-image:
      linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
    background-size: 60px 60px;
    pointer-events: none;
  }

  &__inner {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    align-items: center;
    max-width: 520px;
    width: 100%;
    margin-top: -4vh;
  }

  &__illustration {
    width: 100%;
    max-width: 480px;
    margin-bottom: 28px;

    svg {
      width: 100%;
      height: auto;
      filter: drop-shadow(0 4px 32px rgba(99, 180, 255, 0.10));
    }
  }
}

.login-form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafbfc;
}

.login-card {
  width: 440px;
  max-width: 90%;
  padding: 48px 40px 32px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.05);
}

.login-header {
  margin-bottom: 32px;

  h1 {
    font-size: 26px;
    font-weight: 700;
    color: $login-heading-color;
    letter-spacing: 0.5px;
    margin-bottom: 6px;
  }

  p {
    font-size: 14px;
    color: $login-subtitle-color;
  }
}

.login-card {
  :deep(.el-input) {
    .el-input__wrapper {
      height: 46px;
      padding: 0 15px;
      background-color: $login-input-bg;
      border: 1.5px solid transparent;
      border-radius: 10px;
      box-shadow: none;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

      &:hover {
        background-color: $login-input-bg-focus;
        border-color: $border-base;
      }

      &.is-focus {
        background-color: $login-input-bg-focus;
        border-color: $login-brand-blue;
        box-shadow: 0 0 0 3px rgba($login-brand-blue, 0.12);
      }
    }

    .el-input__prefix .el-icon,
    .el-input__prefix-inner > svg {
      color: $text-placeholder;
    }

    .el-input__inner {
      font-size: 14px;
      color: $login-heading-color;

      &::placeholder {
        color: $text-placeholder;
      }
    }
  }

  :deep(.el-form-item) {
    margin-bottom: 18px;
  }
}

.login-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  color: #ffffff;
  background: linear-gradient(135deg, $login-brand-blue 0%, #3385ff 100%);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba($login-brand-blue, 0.3);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: linear-gradient(135deg, $login-brand-blue-dark 0%, $login-brand-blue 100%);
    box-shadow: 0 6px 20px rgba($login-brand-blue, 0.4);
  }

  &:active {
    transform: scale(0.98);
    box-shadow: 0 2px 10px rgba($login-brand-blue, 0.3);
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }

  &__loading {
    display: inline-flex;
    align-items: center;
    justify-content: center;

    i {
      display: inline-block;
      width: 18px;
      height: 18px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: #ffffff;
      border-radius: 50%;
      animation: login-spin 0.6s linear infinite;
    }
  }
}

@keyframes login-spin {
  to {
    transform: rotate(360deg);
  }
}

.code-btn {
  flex-shrink: 0;
  width: 120px;
  height: 46px;
  font-size: 13px;
  font-weight: 500;
  color: $login-brand-blue;
  background: rgba($login-brand-blue, 0.08);
  border: 1.5px solid rgba($login-brand-blue, 0.2);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;

  &:hover:not(:disabled) {
    background: rgba($login-brand-blue, 0.14);
    border-color: rgba($login-brand-blue, 0.35);
  }

  &:disabled {
    color: $text-disabled;
    background: $login-input-bg;
    border-color: transparent;
    cursor: not-allowed;
  }
}

.login-footer {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 8px;

  &__link {
    font-size: 13px;
    color: $text-placeholder;
    text-decoration: none;
    transition: color 0.25s ease;

    &:hover {
      color: $login-brand-blue;
    }
  }
}

.login-copyright {
  margin-top: 40px;
  text-align: center;

  p {
    font-size: 12px;
    color: $text-disabled;
  }
}

@media screen and (max-width: 1024px) {
  .login-brand {
    width: 45%;
    min-width: 340px;
    padding: 32px 32px;

    &__inner {
      max-width: 360px;
      margin-top: -2vh;
    }

    &__illustration {
      max-width: 340px;
      margin-bottom: 20px;
    }
  }
}

@media screen and (max-width: 768px) {
  .register-page {
    flex-direction: column;
  }

  .login-brand {
    display: none;
  }

  .login-form-panel {
    flex: 1;
    background: linear-gradient(180deg, #f0f4ff 0%, #fafbfc 100%);
    padding: 24px;
  }

  .login-card {
    padding: 36px 28px 24px;
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08);
  }

  .login-header {
    margin-bottom: 28px;

    h1 {
      font-size: 22px;
    }
  }
}
</style>
