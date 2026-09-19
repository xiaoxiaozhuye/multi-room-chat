<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { listRooms } from '@/api/chat'
import type { ChatRoom } from '@/types/api'
import { roomStatusText, roomStatusType } from '@/utils/presentation'

const router = useRouter()
const loading = ref(false)
const rooms = ref<ChatRoom[]>([])
const hasNext = ref(false)
const filters = reactive<{ name: string; page: number }>({ name: '', page: 1 })

async function load(reset = false): Promise<void> {
  if (reset) filters.page = 1
  loading.value = true
  try {
    const data = await listRooms({ name: filters.name || undefined, roomStatus: 'ACTIVE', page: filters.page, size: 12 })
    rooms.value = reset ? data.items : [...rooms.value, ...data.items]
    hasNext.value = data.hasNext
  } finally { loading.value = false }
}

function showRoom(roomId: string): void { void router.push({ name: 'room-detail', params: { roomId } }) }
function next(): void { filters.page += 1; void load() }
onMounted(() => { void load(true) })
</script>

<template>
  <section class="page-stack discover-page">
    <div class="discover-top">
      <div>
        <h1>发现聊天室</h1>
        <p>浏览并加入感兴趣的聊天室</p>
      </div>
      <el-input v-model.trim="filters.name" class="discover-search" placeholder="按名称搜索" :prefix-icon="Search" clearable @keyup.enter="load(true)" @clear="load(true)" />
    </div>
    <el-skeleton v-if="loading && rooms.length === 0" :rows="6" animated />
    <el-empty v-else-if="rooms.length === 0" description="未找到符合条件的聊天室"><el-button type="primary" @click="filters.name = ''; load(true)">清除搜索</el-button></el-empty>
    <div v-else class="room-grid">
      <article v-for="room in rooms" :key="room.roomId" class="room-card" tabindex="0" @click="showRoom(room.roomId)" @keyup.enter="showRoom(room.roomId)">
        <div class="room-card-top"><h2>{{ room.name }}</h2><el-tag :type="roomStatusType(room.roomStatus)" effect="light">{{ roomStatusText(room.roomStatus) }}</el-tag></div>
        <p class="room-description">{{ room.description || '暂未填写房间介绍。' }}</p>
        <p class="room-meta">{{ room.activeMemberCount }} / {{ room.maxMembers }} 人 · {{ room.joinMode === 'OPEN' ? '公开加入' : '需要审批' }}</p>
        <el-button type="primary" plain @click.stop="showRoom(room.roomId)">查看详情</el-button>
      </article>
    </div>
    <div v-if="hasNext" class="load-more"><el-button :loading="loading" @click="next">加载更多</el-button></div>
  </section>
</template>
