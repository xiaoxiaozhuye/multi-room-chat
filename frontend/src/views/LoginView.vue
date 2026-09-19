<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { register } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const registerMode = ref(false)
const rememberedCredentialsKey = 'multi-room-chat:remembered-credentials'

function readRememberedCredentials(): { username: string; password: string } | null {
  try {
    const raw = localStorage.getItem(rememberedCredentialsKey)
    if (!raw) return null
    const credentials = JSON.parse(raw) as Partial<{ username: string; password: string }>
    if (typeof credentials.username !== 'string' || typeof credentials.password !== 'string') return null
    return { username: credentials.username, password: credentials.password }
  } catch {
    return null
  }
}

const rememberedCredentials = readRememberedCredentials()
const rememberPassword = ref(Boolean(rememberedCredentials))
const form = reactive({
  username: rememberedCredentials?.username ?? 'admin',
  email: '',
  password: rememberedCredentials?.password ?? 'Admin123!',
})
const title = computed(() => registerMode.value ? '创建账号' : '登录')

async function submit(): Promise<void> {
  loading.value = true
  try {
    if (registerMode.value) await auth.applySession(await register({ username: form.username, email: form.email, password: form.password }))
    else {
      await auth.signIn(form)
      if (rememberPassword.value) {
        localStorage.setItem(rememberedCredentialsKey, JSON.stringify({ username: form.username, password: form.password }))
      } else {
        localStorage.removeItem(rememberedCredentialsKey)
      }
    }
    const admin = auth.user?.roles.some((role) => role === 'ROOM_ADMIN' || role === 'SYSTEM_ADMIN')
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : admin ? '/admin/workbench' : '/rooms')
  } finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <el-card class="login-card" shadow="never">
      <template #header><h1>研聊 · {{ title }}</h1></template>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item v-if="registerMode" label="邮箱" required><el-input v-model.trim="form.email" type="email" maxlength="254" autocomplete="email" /></el-form-item>
        <el-form-item label="用户名" required><el-input v-model="form.username" autocomplete="username" /></el-form-item>
        <el-form-item label="密码" required><el-input v-model="form.password" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-checkbox v-if="!registerMode" v-model="rememberPassword">记住密码</el-checkbox>
        <el-button type="primary" native-type="submit" :loading="loading" class="login-button">{{ registerMode ? '注册并登录' : '登录' }}</el-button>
        <el-button text class="mode-button" @click="registerMode = !registerMode">{{ registerMode ? '已有账号？去登录' : '还没有账号？创建账号' }}</el-button>
      </el-form>
    </el-card>
  </main>
</template>

<style scoped>
.login-page { display: grid; min-height: 100vh; place-items: center; background: #f6f7f9; }
.login-card { width: min(420px, calc(100vw - 32px)); }
h1 { margin: 0; font-size: 22px; }
.login-button { width: 100%; }
.mode-button { display: block; margin: 10px auto 0; }
</style>
