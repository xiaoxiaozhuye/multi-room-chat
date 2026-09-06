<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Connection, SwitchButton } from '@element-plus/icons-vue'
import { appRoutes, type AppRouteRecord } from '@/router/routes'
import { useAuthStore } from '@/stores/auth'
import { chatWebSocket } from '@/websocket/client'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const connectionStatus = ref(chatWebSocket.status)
let removeWebSocketListener: (() => void) | undefined
const root = appRoutes.find((item) => item.path === '/') as AppRouteRecord
const menuGroups = computed(() => {
  const groups = new Map<string, AppRouteRecord[]>()
  for (const item of root.children ?? []) {
    if (!item.meta.menu || !auth.hasAnyRole(item.meta.roles)) continue
    const section = item.meta.section ?? '用户端'
    groups.set(section, [...(groups.get(section) ?? []), item])
  }
  return [...groups]
})

async function signOut(): Promise<void> {
  await auth.signOut()
  await router.replace({ name: 'login' })
}

onMounted(() => {
  removeWebSocketListener = chatWebSocket.on((event) => {
    if (event.type === 'CONNECTION_STATUS') connectionStatus.value = String((event.payload as { status?: string }).status ?? 'idle')
  })
})

onBeforeUnmount(() => removeWebSocketListener?.())
</script>

<template>
  <el-container class="app-shell">
    <el-aside width="230px" class="sidebar">
      <div class="brand">多聊天室群聊</div>
      <el-menu :default-active="String(route.name)" router>
        <template v-for="[section, entries] in menuGroups" :key="section">
          <div class="menu-section">{{ section }}</div>
          <el-menu-item v-for="entry in entries" :key="String(entry.name)" :index="String(entry.name)" :route="{ name: entry.name }">
            {{ entry.meta.title }}
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>{{ String(route.meta.title ?? '多聊天室群聊') }}</span>
        <div class="header-actions">
          <el-tag :type="connectionStatus === 'connected' ? 'success' : 'info'" effect="plain">
            <el-icon><Connection /></el-icon> {{ connectionStatus === 'connected' ? '实时已连接' : '实时未连接' }}
          </el-tag>
          <el-dropdown>
            <span class="user-name">{{ auth.user?.displayName ?? auth.user?.username }}</span>
            <template #dropdown><el-dropdown-menu><el-dropdown-item :icon="SwitchButton" @click="signOut">退出登录</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-shell { min-height: 100vh; }
.sidebar { border-right: 1px solid var(--el-border-color-light); background: #fff; }
.brand { padding: 24px 20px 20px; color: var(--el-color-primary); font-size: 18px; font-weight: 700; }
.menu-section { padding: 16px 20px 6px; color: var(--el-text-color-secondary); font-size: 12px; }
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--el-border-color-light); background: #fff; font-weight: 600; }
.header-actions { display: flex; align-items: center; gap: 18px; font-weight: 400; }
.user-name { cursor: pointer; color: var(--el-text-color-regular); }
</style>
