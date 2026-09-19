<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import { ArrowLeft, Connection, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { appRoutes, type AppRouteRecord } from '@/router/routes'
import { useAuthStore } from '@/stores/auth'
import { chatWebSocket, type ConnectionStatus } from '@/websocket/client'
import { getRoom } from '@/api/chat'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const connectionStatus = ref(chatWebSocket.status)
const sessionReplaced = ref(false)
const chatRoomTitle = ref('')
const connectionProblem = computed(() => connectionStatus.value === 'reconnecting' || connectionStatus.value === 'closed')
const fixedTopbar = computed(() => [
  'rooms', 'my-rooms', 'my', 'profile', 'room-detail', 'my-messages', 'chat',
  'admin-workbench', 'admin-rooms', 'admin-join-requests', 'admin-review-messages',
  'admin-room-moderation', 'admin-broadcasts', 'admin-authorizations', 'admin-audits',
  'admin-metrics',
].includes(String(route.name)))
const contentOffset = computed(() => fixedTopbar.value && route.name !== 'chat')
const collapsedMenuSections = ref<Record<string, boolean>>({})
const contentRefreshKey = ref(0)
interface OpenTab { key: string; title: string; target: RouteLocationRaw }
const openTabsStorageKey = 'multi-room-chat:open-tabs'
function readOpenTabs(): OpenTab[] {
  try {
    const raw = sessionStorage.getItem(openTabsStorageKey)
    const parsed = raw ? JSON.parse(raw) : []
    if (!Array.isArray(parsed)) return []
    return parsed.filter((tab): tab is { key: string; title: string } => typeof tab?.key === 'string' && typeof tab?.title === 'string')
      .slice(-15)
      .map((tab) => ({ key: tab.key, title: tab.title, target: { path: tab.key } }))
  } catch { return [] }
}
const openTabs = ref<OpenTab[]>(readOpenTabs())
const tabContextMenu = ref<{ x: number; y: number; key: string } | null>(null)
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
const bottomNavEntries = computed(() => [
  { name: 'rooms', label: '发现' },
  { name: 'my-rooms', label: '消息' },
  { name: 'my', label: '我的' },
])
function isBottomNavActive(name: string): boolean {
  if (name !== 'my') return route.name === name
  return ['my', 'profile', 'admin-workbench'].includes(String(route.name))
}
async function goTo(name: string): Promise<void> {
  await router.push({ name })
}
function toggleMenuSection(section: string): void {
  collapsedMenuSections.value = {
    ...collapsedMenuSections.value,
    [section]: !collapsedMenuSections.value[section],
  }
}
function isMenuSectionExpanded(section: string): boolean {
  return collapsedMenuSections.value[section] !== true
}
function userInitial(): string {
  return (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase()
}
const activeTabKey = computed(() => route.fullPath)
function currentTabTitle(): string {
  return route.name === 'chat' && chatRoomTitle.value ? chatRoomTitle.value : String(route.meta.title ?? '工作区')
}
function addCurrentTab(): void {
  if (!route.name) return
  const key = route.fullPath
  const existing = openTabs.value.find((tab) => tab.key === key)
  if (existing) {
    existing.title = currentTabTitle()
    return
  }
  if (openTabs.value.length >= 15) openTabs.value.shift()
  openTabs.value.push({ key, title: currentTabTitle(), target: { path: route.fullPath } })
}
function switchTab(key: string): void {
  const tab = openTabs.value.find((item) => item.key === key)
  if (tab) void router.push(tab.target)
}
function openTabContextMenu(event: MouseEvent, key: string): void {
  event.preventDefault()
  tabContextMenu.value = {
    x: Math.min(event.clientX, window.innerWidth - 156),
    y: Math.min(event.clientY, window.innerHeight - 228),
    key,
  }
}
function closeTabContextMenu(): void { tabContextMenu.value = null }
function closeTabKeys(keys: string[]): void {
  const removeKeys = new Set(keys)
  const activeWasClosed = removeKeys.has(activeTabKey.value)
  const activeIndex = openTabs.value.findIndex((tab) => tab.key === activeTabKey.value)
  openTabs.value = openTabs.value.filter((tab) => !removeKeys.has(tab.key))
  closeTabContextMenu()
  if (activeWasClosed) {
    const fallback = openTabs.value[Math.min(Math.max(activeIndex, 0), openTabs.value.length - 1)]
    if (fallback) void router.push(fallback.target)
  }
}
function closeContextTab(): void {
  if (tabContextMenu.value) closeTabKeys([tabContextMenu.value.key])
}
async function refreshContextTab(): Promise<void> {
  const key = tabContextMenu.value?.key
  const tab = openTabs.value.find((item) => item.key === key)
  closeTabContextMenu()
  if (!tab) return
  if (tab.key !== activeTabKey.value) await router.push(tab.target)
  contentRefreshKey.value += 1
  ElMessage.success('页面已刷新')
}
function closeContextSide(side: 'left' | 'right'): void {
  const key = tabContextMenu.value?.key
  if (!key) return
  const index = openTabs.value.findIndex((tab) => tab.key === key)
  if (index < 0) return
  closeTabKeys(side === 'left' ? openTabs.value.slice(0, index).map((tab) => tab.key) : openTabs.value.slice(index + 1).map((tab) => tab.key))
}
function closeContextOthers(): void {
  const key = tabContextMenu.value?.key
  if (key) closeTabKeys(openTabs.value.filter((tab) => tab.key !== key).map((tab) => tab.key))
}
function closeContextAll(): void {
  openTabs.value = []
  closeTabContextMenu()
  if (route.name === 'rooms') addCurrentTab()
  else void router.push({ name: 'rooms' })
}
function closeTab(key: string): void {
  if (openTabs.value.length <= 1) return
  const index = openTabs.value.findIndex((tab) => tab.key === key)
  if (index < 0) return
  const wasActive = key === activeTabKey.value
  openTabs.value.splice(index, 1)
  if (wasActive) {
    const nextTab = openTabs.value[index] ?? openTabs.value[index - 1]
    if (nextTab) void router.push(nextTab.target)
  }
}

watch(openTabs, (tabs) => {
  try { sessionStorage.setItem(openTabsStorageKey, JSON.stringify(tabs)) } catch { /* Storage may be unavailable. */ }
}, { deep: true, immediate: true })
watch(() => [route.name, String(route.params.roomId ?? '')], async ([name, roomId]) => {
  if (String(name) !== 'chat' || !roomId) { chatRoomTitle.value = ''; return }
  chatRoomTitle.value = ''
  try { chatRoomTitle.value = (await getRoom(String(roomId))).name } catch { chatRoomTitle.value = '' }
}, { immediate: true })

watch(() => route.fullPath, addCurrentTab, { immediate: true })
watch(chatRoomTitle, () => {
  const current = openTabs.value.find((tab) => tab.key === route.fullPath)
  if (current) current.title = currentTabTitle()
})

async function signOut(): Promise<void> {
  await auth.signOut()
  await router.replace({ name: 'login' })
}

onMounted(() => {
  // The websocket is started in main.ts before AppLayout mounts. Read the
  // current value first so a fast handshake cannot leave the header stuck at
  // the initial "connecting" state after the CONNECTED event was emitted.
  connectionStatus.value = chatWebSocket.status
  removeWebSocketListener = chatWebSocket.on((event) => {
    if (event.type === 'CONNECTION_STATUS') {
      const status = (event.payload as { status?: string }).status
      if (status && ['idle', 'connecting', 'connected', 'reconnecting', 'closed'].includes(status)) connectionStatus.value = status as ConnectionStatus
    }
    if (event.type === 'ERROR' && (event.payload as { code?: string }).code === 'SESSION_REPLACED') sessionReplaced.value = true
  })
})

onBeforeUnmount(() => {
  removeWebSocketListener?.()
})
</script>

<template>
  <el-container class="app-shell" :class="{ 'fixed-topbar-shell': fixedTopbar, 'content-offset-shell': contentOffset }">
    <el-aside width="230px" class="sidebar">
      <div class="brand">多聊天室群聊</div>
      <el-menu :default-active="String(route.name)" router>
        <template v-for="[section, entries] in menuGroups" :key="section">
          <button class="menu-section" :class="{ 'is-active': entries.some((entry) => String(entry.name) === String(route.name)) }" type="button" :aria-expanded="isMenuSectionExpanded(section)" @click="toggleMenuSection(section)">
            <span>{{ section }}</span><span class="menu-section-arrow" aria-hidden="true">{{ isMenuSectionExpanded(section) ? '⌄' : '›' }}</span>
          </button>
          <template v-if="isMenuSectionExpanded(section)">
            <el-menu-item v-for="entry in entries" :key="String(entry.name)" :index="String(entry.name)" :route="{ name: entry.name }">
              {{ entry.meta.title }}
            </el-menu-item>
          </template>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header" :class="{ 'fixed-topbar': fixedTopbar, 'chat-topbar': route.name === 'chat', 'profile-topbar': route.name === 'profile' }">
        <el-button v-if="route.name === 'chat'" class="chat-back-button" text circle :icon="ArrowLeft" aria-label="返回我的聊天室" @click="router.push({ name: 'my-rooms' })" />
        <el-button v-if="route.name === 'profile'" class="profile-back-button" text circle :icon="ArrowLeft" aria-label="返回我的" @click="router.push({ name: 'my' })" />
        <el-button v-if="route.name === 'room-detail'" class="room-detail-back-button" text circle :icon="ArrowLeft" aria-label="返回发现" @click="router.push({ name: 'rooms' })" />
        <el-button v-if="route.name === 'admin-workbench'" class="admin-workbench-back-button" text circle :icon="ArrowLeft" aria-label="返回我的" @click="router.push({ name: 'my' })" />
        <el-button v-if="['admin-rooms', 'admin-metrics', 'admin-join-requests', 'admin-review-messages', 'admin-room-moderation', 'admin-broadcasts', 'admin-authorizations', 'admin-audits', 'my-messages'].includes(String(route.name))" class="admin-subpage-back-button" text circle :icon="ArrowLeft" aria-label="返回管理工作台" @click="router.push({ name: 'admin-workbench' })" />
        <span>{{ route.name === 'chat' ? chatRoomTitle : String(route.meta.title ?? '多聊天室群聊') }}</span>
        <div class="header-actions">
          <el-tag class="desktop-connection-status" :type="connectionStatus === 'connected' ? 'success' : connectionStatus === 'reconnecting' ? 'warning' : 'info'" effect="plain">
            <el-icon><Connection /></el-icon> {{ connectionStatus === 'connected' ? '实时已连接' : connectionStatus === 'reconnecting' ? '正在重连…' : '离线，消息可能延迟' }}
          </el-tag>
          <el-dropdown class="desktop-user-menu" placement="bottom-end">
            <button class="avatar-trigger" type="button" aria-label="打开用户菜单">
              <el-avatar :size="34" :src="auth.user?.avatarUrl || undefined">{{ userInitial() }}</el-avatar>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push({ name: 'profile' })">个人资料</el-dropdown-item>
                <el-dropdown-item :icon="SwitchButton" @click="signOut">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-tag v-if="connectionProblem" class="mobile-connection-alert" type="danger" effect="plain">连接异常</el-tag>
        </div>
      </el-header>
      <div class="desktop-tab-bar" aria-label="已打开页面" @click="closeTabContextMenu">
        <div class="desktop-tabs">
          <button v-for="tab in openTabs" :key="tab.key" type="button" class="desktop-tab" :class="{ active: tab.key === activeTabKey }" @click.stop="switchTab(tab.key)" @contextmenu.stop="openTabContextMenu($event, tab.key)">
            <span class="desktop-tab-title">{{ tab.title }}</span>
            <span v-if="openTabs.length > 1" class="desktop-tab-close" aria-label="关闭标签" @click.stop="closeTab(tab.key)">×</span>
          </button>
        </div>
        <div v-if="tabContextMenu" class="tab-context-menu" :style="{ left: `${tabContextMenu.x}px`, top: `${tabContextMenu.y}px` }" @click.stop>
          <button type="button" @click="refreshContextTab">刷新</button>
          <button type="button" :disabled="openTabs.length <= 1" @click="closeContextTab">关闭当前</button>
          <button type="button" @click="closeContextSide('right')">关闭右侧</button>
          <button type="button" @click="closeContextSide('left')">关闭左侧</button>
          <button type="button" @click="closeContextOthers">关闭其他</button>
          <button type="button" @click="closeContextAll">全部关闭</button>
        </div>
      </div>
      <el-main><router-view :key="`${route.fullPath}:${contentRefreshKey}`" /></el-main>
    </el-container>
  </el-container>
  <nav class="mobile-bottom-nav" aria-label="移动端主导航">
    <button v-for="entry in bottomNavEntries" :key="entry.name" type="button" :class="{ active: isBottomNavActive(entry.name) }" @click="goTo(entry.name)">
      {{ entry.label }}
    </button>
  </nav>
  <el-dialog v-model="sessionReplaced" title="此账号已在其他设备继续使用" width="360px" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
    <p>当前页面已停止自动重连，请重新登录后继续使用。</p>
    <template #footer><el-button type="primary" @click="signOut">重新登录</el-button></template>
  </el-dialog>
</template>

<style scoped>
.app-shell { min-height: 100vh; }
.sidebar { border-right: 1px solid #eaecf0; background: #fff; scrollbar-color: #d0d5dd transparent; scrollbar-width: thin; }
.brand { padding: 26px 24px 22px; color: var(--el-color-primary); font-size: 22px; font-weight: 700; letter-spacing: -.02em; }
.sidebar :deep(.el-menu) { border-right: 0; background: transparent; }
.sidebar :deep(.el-menu-item) { height: 42px; margin: 2px 10px 2px 20px; padding-right: 14px !important; padding-left: 20px !important; border-left: 1px solid #eaecf0; border-radius: 0 8px 8px 0; color: #344054; font-size: 14px; line-height: 42px; }
.sidebar :deep(.el-menu-item:hover) { background: #f2f4f7; color: var(--el-color-primary); }
.sidebar :deep(.el-menu-item.is-active) { position: relative; background: #eef4ff; color: var(--el-color-primary); font-weight: 600; }
.sidebar :deep(.el-menu-item.is-active)::before { position: absolute; top: 10px; bottom: 10px; left: 0; width: 3px; border-radius: 0 3px 3px 0; background: var(--el-color-primary); content: ''; }
.menu-section { display: flex; width: 100%; align-items: center; justify-content: space-between; padding: 16px 20px 7px; border: 0; background: transparent; color: #98a2b3; cursor: pointer; font: inherit; font-size: 12px; font-weight: 600; letter-spacing: .02em; text-align: left; }
.menu-section:hover, .menu-section.is-active { color: var(--el-color-primary); }
.menu-section.is-active { background: linear-gradient(90deg, #f5f8ff, transparent); }
.menu-section-arrow { font-size: 16px; line-height: 1; }
.header { position: relative; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--el-border-color-light); background: #fff; font-weight: 600; }
.header.fixed-topbar { position: fixed; z-index: 1003; top: 0; right: 0; left: 0; }
.header > span { position: absolute; left: 50%; max-width: 60%; overflow: hidden; transform: translateX(-50%); text-overflow: ellipsis; white-space: nowrap; text-align: center; }
.header-actions { display: flex; align-items: center; gap: 18px; font-weight: 400; }
.avatar-trigger { display: inline-flex; padding: 0; border: 0; border-radius: 50%; background: transparent; cursor: pointer; }
.avatar-trigger:focus-visible { outline: 2px solid rgb(36 87 214 / 40%); outline-offset: 3px; }
.desktop-tab-bar { display: none; }
@media (min-width: 761px) {
  .header-actions { position: absolute; top: 1px; right: 20px; pointer-events: auto; }
  /* Keep the workspace tabs in the compact top bar. The 112px right offset
     leaves 40px between the tab edge and the connection status tag. */
  .desktop-tab-bar { position: relative; z-index: 1000; top: auto; right: auto; left: auto; display: block; width: 100%; margin-top: 0; padding: 0 112px 5px 20px; border-bottom: 1px solid #e4e7ec; background: #fff; box-shadow: 0 2px 8px rgb(16 24 40 / 3%); }
  .desktop-tabs { display: flex; align-items: flex-end; gap: 6px; overflow-x: auto; scrollbar-width: thin; }
  .desktop-tab { display: inline-flex; width: 148px; min-width: 96px; max-width: 172px; min-height: 36px; flex: 0 1 148px; align-items: center; gap: 8px; padding: 0 12px; border: 1px solid #e4e7ec; border-bottom: 0; border-radius: 8px 8px 0 0; background: #f8fafc; color: #667085; cursor: pointer; font: inherit; font-size: 14px; transition: background .18s, border-color .18s, color .18s, box-shadow .18s; }
  .desktop-tab:hover { border-color: #c9d7f5; background: #f2f6ff; color: var(--el-color-primary); }
  .desktop-tab.active { position: relative; z-index: 1; border-color: #c9d7f5; background: #fff; color: var(--el-color-primary); font-weight: 600; box-shadow: 0 -2px 0 var(--el-color-primary); }
  .desktop-tab-title { min-width: 0; overflow: hidden; flex: 1 1 auto; text-overflow: ellipsis; white-space: nowrap; }
  .desktop-tab-close { width: 18px; height: 18px; flex: 0 0 18px; border-radius: 50%; color: #98a2b3; font-size: 14px; line-height: 17px; text-align: center; transition: background .18s, color .18s; }
  .desktop-tab-close:hover { background: #e4e7ec; color: #344054; }
  .tab-context-menu { position: fixed; z-index: 2000; display: grid; width: 140px; padding: 6px 0; border: 1px solid var(--el-border-color-light); border-radius: 6px; background: #fff; box-shadow: 0 6px 18px rgb(0 0 0 / 14%); }
  .tab-context-menu button { padding: 8px 14px; border: 0; background: transparent; color: var(--el-text-color-regular); cursor: pointer; font: inherit; text-align: left; }
  .tab-context-menu button:hover:not(:disabled) { background: #f5f7fa; color: var(--el-color-primary); }
  .tab-context-menu button:disabled { color: var(--el-text-color-placeholder); cursor: not-allowed; }
}
</style>
