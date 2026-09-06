<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { listRooms } from '@/api/chat'
import type { ChatRoom, JoinMode, RoomStatus } from '@/types/api'
import { roomStatusText, roomStatusType } from '@/utils/presentation'

const router = useRouter()
const loading = ref(false)
const rooms = ref<ChatRoom[]>([])
const hasNext = ref(false)
const filters = reactive<{ name: string; roomStatus?: RoomStatus; joinMode?: JoinMode; page: number }>({ name: '', roomStatus: 'ACTIVE', page: 1 })

async function load(reset = false): Promise<void> {
  if (reset) filters.page = 1
  loading.value = true
  try {
    const data = await listRooms({ ...filters, name: filters.name || undefined, size: 12 })
    rooms.value = reset ? data.items : [...rooms.value, ...data.items]
    hasNext.value = data.hasNext
  } finally { loading.value = false }
}

function showRoom(roomId: string): void { void router.push({ name: 'room-detail', params: { roomId } }) }
function next(): void { filters.page += 1; void load() }
onMounted(() => { void load(true) })
</script>

<template>
  <section class="page-stack">
    <div class="page-heading"><div><h1>发现聊天室</h1><p>选择感兴趣的投研讨论间，加入后可查看历史并参与实时交流。</p></div></div>
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="load(true)">
        <el-form-item label="房间名称"><el-input v-model.trim="filters.name" placeholder="按名称搜索" :prefix-icon="Search" clearable /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.roomStatus" clearable placeholder="全部状态"><el-option label="进行中" value="ACTIVE" /><el-option label="已暂停" value="PAUSED" /><el-option label="已关闭" value="CLOSED" /></el-select></el-form-item>
        <el-form-item label="加入方式"><el-select v-model="filters.joinMode" clearable placeholder="全部方式"><el-option label="公开加入" value="OPEN" /><el-option label="需要审批" value="APPROVAL" /></el-select></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading">筛选</el-button>
      </el-form>
    </el-card>
    <el-skeleton v-if="loading && rooms.length === 0" :rows="6" animated />
    <el-empty v-else-if="rooms.length === 0" description="未找到符合条件的聊天室"><el-button type="primary" @click="filters.name = ''; filters.roomStatus = 'ACTIVE'; filters.joinMode = undefined; load(true)">清除筛选</el-button></el-empty>
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
