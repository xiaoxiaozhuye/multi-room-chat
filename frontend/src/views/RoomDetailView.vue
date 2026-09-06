<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRoom, joinRoom, listMyRooms } from '@/api/chat'
import type { ChatRoom, Membership } from '@/types/api'
import { roomStatusText, roomStatusType } from '@/utils/presentation'

const route = useRoute(); const router = useRouter()
const room = ref<ChatRoom>(); const membership = ref<Membership>(); const loading = ref(true); const joining = ref(false)
const roomId = computed(() => String(route.params.roomId))
const isFull = computed(() => !!room.value && room.value.activeMemberCount >= room.value.maxMembers)
const action = computed(() => {
  if (!room.value) return '加载中'
  if (membership.value?.memberStatus === 'ACTIVE') return '进入聊天室'
  if (membership.value?.memberStatus === 'PENDING') return '申请已提交，等待审批'
  if (room.value.roomStatus !== 'ACTIVE') return '当前不可加入'
  if (isFull.value) return '房间已满'
  return room.value.joinMode === 'OPEN' ? '立即加入' : '申请加入'
})
const disabled = computed(() => !!membership.value || room.value?.roomStatus !== 'ACTIVE' || isFull.value)
async function load(): Promise<void> {
  loading.value = true
  try { room.value = await getRoom(roomId.value); const mine = await listMyRooms({ size: 100 }); membership.value = mine.items.find((item) => item.roomId === roomId.value && ['ACTIVE', 'PENDING'].includes(item.memberStatus)) } finally { loading.value = false }
}
async function primary(): Promise<void> {
  if (membership.value?.memberStatus === 'ACTIVE') { await router.push({ name: 'chat', params: { roomId: roomId.value } }); return }
  if (disabled.value) return
  joining.value = true
  try { const result = await joinRoom(roomId.value); membership.value = result.membership; ElMessage.success(result.joinResult === 'JOINED' ? '已加入聊天室。' : '申请已提交，等待管理员审批。'); if (result.joinResult === 'JOINED') await router.push({ name: 'chat', params: { roomId: roomId.value } }) } finally { joining.value = false }
}
onMounted(() => { void load() })
</script>

<template>
  <section class="detail-page">
    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else-if="room">
      <el-button text @click="router.push({ name: 'rooms' })">← 返回发现聊天室</el-button>
      <el-card shadow="never" class="room-detail-card"><div class="room-card-top"><div><h1>{{ room.name }}</h1><p class="room-description">{{ room.description || '暂未填写房间介绍。' }}</p></div><el-tag :type="roomStatusType(room.roomStatus)">{{ roomStatusText(room.roomStatus) }}</el-tag></div><p class="room-meta">{{ room.activeMemberCount }} / {{ room.maxMembers }} 人 · {{ room.joinMode === 'OPEN' ? '公开加入' : '需要审批' }}</p><el-button type="primary" size="large" :disabled="disabled && membership?.memberStatus !== 'ACTIVE'" :loading="joining" @click="primary">{{ action }}</el-button></el-card>
      <el-alert v-if="membership?.memberStatus === 'PENDING'" type="warning" :closable="false" title="你的申请正在等待审批；本期暂不支持撤回申请。" />
      <el-alert v-else-if="room.roomStatus !== 'ACTIVE'" type="info" :closable="false" :title="`${roomStatusText(room.roomStatus)}的房间暂不可加入；已加入成员仍可按权限查看历史。`" />
      <el-card shadow="never"><template #header>加入说明</template><p>公开加入的房间会立即成为成员；需要审批的房间在管理员批准前不可进入聊天。普通用户消息需要审核后才会公开展示。</p></el-card>
    </template>
  </section>
</template>
