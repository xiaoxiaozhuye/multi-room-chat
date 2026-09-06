<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit(): Promise<void> {
  loading.value = true
  try {
    await auth.signIn(form)
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/rooms')
  } finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <el-card class="login-card" shadow="never">
      <template #header><h1>多聊天室群聊</h1></template>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" required><el-input v-model="form.username" autocomplete="username" /></el-form-item>
        <el-form-item label="密码" required><el-input v-model="form.password" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="login-button">登录</el-button>
      </el-form>
    </el-card>
  </main>
</template>

<style scoped>
.login-page { display: grid; min-height: 100vh; place-items: center; background: linear-gradient(135deg, #ecf5ff, #f7fbff); }
.login-card { width: min(420px, calc(100vw - 32px)); }
h1 { margin: 0; font-size: 22px; }
.login-button { width: 100%; }
</style>
