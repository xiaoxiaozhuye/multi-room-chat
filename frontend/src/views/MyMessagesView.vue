<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listMyMessages } from '@/api/chat'
import SafeText from '@/components/SafeText.vue'
import type { ChatMessage, MessageStatus } from '@/types/api'
import { formatTime, messageStatusText, messageStatusType } from '@/utils/presentation'

const router = useRouter(); const loading = ref(false); const loadingMore = ref(false); const messages = ref<ChatMessage[]>([]); const status = ref<MessageStatus | undefined>(); const cursor = ref<string>(); const hasMore = ref(false)
async function load(reset = false): Promise<void> { if (reset) cursor.value = undefined; (reset ? loading : loadingMore).value = true; try { const data = await listMyMessages({ messageStatus: status.value, cursor: cursor.value }); messages.value = reset ? data.items : [...messages.value, ...data.items]; cursor.value = data.nextCursor ?? undefined; hasMore.value = Boolean(data.hasMore ?? data.nextCursor) } finally { loading.value = false; loadingMore.value = false } }
function roomLink(item: ChatMessage): void { if (!item.roomDeleted) void router.push({ name: 'chat', params: { roomId: item.roomId } }) }
onMounted(() => { void load(true) })
</script>

<template>
  <section class="page-stack"><div class="page-heading"><div><h1>我的消息</h1><p>这里显示你提交的消息及最新审核状态；审核中的内容不会公开给其他成员。</p></div><el-select v-model="status" clearable placeholder="全部状态" @change="load(true)"><el-option label="审核中" value="PENDING_REVIEW" /><el-option label="等待按序发布" value="APPROVED" /><el-option label="已发布" value="PUBLISHED" /><el-option label="未通过审核" value="REJECTED" /><el-option label="审核超时" value="TIMEOUT" /></el-select></div><el-skeleton v-if="loading" :rows="10" animated /><el-empty v-else-if="messages.length === 0" description="暂无符合条件的消息" /><div v-else class="my-message-list"><article v-for="item in messages" :key="item.messageId" class="my-message-card"><header><div><button class="room-link" :disabled="item.roomDeleted" @click="roomLink(item)">{{ item.roomDeleted ? '房间已删除' : (item.roomName || '查看聊天室') }}</button><time>{{ formatTime(item.createdAt) }}</time></div><el-tag :type="messageStatusType(item.messageStatus)">{{ messageStatusText(item.messageStatus) }}</el-tag></header><SafeText :content="item.content" /><p v-if="item.messageStatus === 'PENDING_REVIEW' && item.reviewDeadlineAt" class="status-note">预计在 {{ formatTime(item.reviewDeadlineAt) }} 前处理</p><p v-else-if="item.messageStatus === 'APPROVED'" class="status-note">消息已通过审核，仍需等待前序消息处理完毕后公开。</p></article></div><div v-if="hasMore" class="load-more"><el-button :loading="loadingMore" @click="load()">加载更多</el-button></div></section>
</template>
