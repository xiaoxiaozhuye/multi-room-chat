<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listMyRooms } from '@/api/chat'
import type { MemberStatus, Membership } from '@/types/api'
import { formatTime } from '@/utils/presentation'

const router = useRouter()
const loading = ref(false)
const status = ref<MemberStatus | undefined>()
const memberships = ref<Membership[]>([])
const emptyText = computed(() => status.value === 'PENDING' ? '暂无等待审批的申请' : '尚未加入任何聊天室')
async function load(): Promise<void> { loading.value = true; try { memberships.value = (await listMyRooms({ memberStatus: status.value, size: 50 })).items } finally { loading.value = false } }
function open(item: Membership): void { if (item.memberStatus === 'ACTIVE') void router.push({ name: 'chat', params: { roomId: item.roomId } }); else void router.push({ name: 'room-detail', params: { roomId: item.roomId } }) }
onMounted(() => { void load() })
</script>

<template>
  <section class="page-stack">
    <div class="page-heading"><div><h1>我的聊天室</h1><p>管理已加入的房间并跟踪等待审批的申请。</p></div><el-select v-model="status" clearable placeholder="全部状态" @change="load"><el-option label="已加入" value="ACTIVE" /><el-option label="等待审批" value="PENDING" /><el-option label="未通过" value="REJECTED" /><el-option label="已退出" value="EXITED" /></el-select></div>
    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="memberships.length === 0" :description="emptyText"><el-button type="primary" @click="router.push({ name: 'rooms' })">去发现聊天室</el-button></el-empty>
    <el-card v-for="item in memberships" v-else :key="item.membershipId" shadow="never" class="membership-card">
      <div><h2>{{ item.room?.name ?? '聊天室' }}</h2><p class="room-meta">{{ item.room?.description || '房间信息暂不可用' }} · 申请于 {{ formatTime(item.createdAt) }}</p></div>
      <div class="membership-action"><el-tag :type="item.memberStatus === 'ACTIVE' ? 'success' : item.memberStatus === 'PENDING' ? 'warning' : 'info'">{{ item.memberStatus === 'ACTIVE' ? '已加入' : item.memberStatus === 'PENDING' ? '已申请，等待审批' : item.memberStatus === 'REJECTED' ? '未通过' : '已退出' }}</el-tag><el-button type="primary" plain @click="open(item)">{{ item.memberStatus === 'ACTIVE' ? '进入聊天室' : '查看详情' }}</el-button></div>
    </el-card>
  </section>
</template>
