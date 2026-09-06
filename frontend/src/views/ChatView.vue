<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRoomMembers, listRoomMessages, listRoomNotifications, getRoom, leaveRoom } from '@/api/chat'
import SafeText from '@/components/SafeText.vue'
import { chatWebSocket } from '@/websocket/client'
import type { ChatMessage, ChatRoom, Membership, MessageStatus } from '@/types/api'
import type { WsEvent } from '@/websocket/protocol'
import { formatTime, messageStatusText, messageStatusType, roomStatusText, roomStatusType } from '@/utils/presentation'

const route = useRoute(); const router = useRouter()
const roomId = computed(() => String(route.params.roomId))
const room = ref<ChatRoom>(); const messages = ref<ChatMessage[]>([]); const privateMessages = ref<ChatMessage[]>([]); const notifications = ref<ChatMessage[]>([])
const draft = ref(''); const loading = ref(true); const olderLoading = ref(false); const sendLoading = ref(false); const messageError = ref(''); const replayGap = ref(false); const realtimeSubscribed = ref(false)
const messageCursor = ref<string | number | undefined>(); const messagesHasMore = ref(false); const memberOpen = ref(false); const notificationOpen = ref(false); const members = ref<Membership[]>([]); const membersLoading = ref(false)
let removeListener: (() => void) | undefined
let reconcileTimer: number | undefined
let reconciling = false

const publishedMessages = computed(() => messages.value.slice().sort((a, b) => Number(a.roomSeq ?? 0) - Number(b.roomSeq ?? 0)))
const canSend = computed(() => room.value?.roomStatus === 'ACTIVE')
const characterCount = computed(() => Array.from(draft.value).length)
const statusById = computed(() => new Map(privateMessages.value.map((item) => [item.messageId, item])))
function maxSequence(items: ChatMessage[], key: 'roomSeq' | 'notificationSeq'): string | undefined {
  return items.reduce<string | undefined>((max, item) => {
    const value = item[key]
    return value !== null && (!max || BigInt(value) > BigInt(max)) ? value : max
  }, undefined)
}
function mergeMessage(target: ChatMessage[], message: ChatMessage): void {
  const index = target.findIndex((item) => item.messageId === message.messageId)
  if (index >= 0) target[index] = { ...target[index], ...message }
  else target.push(message)
}
async function reconcileRecentMessages(): Promise<void> {
  // WebSocket is the primary delivery path. This small, silent reconciliation
  // window prevents a missed frame from leaving the user on stale history.
  if (loading.value || messageError.value || reconciling) return
  reconciling = true
  try {
    const latest = await listRoomMessages(roomId.value, undefined, { silent: true })
    const knownIds = new Set(messages.value.map((item) => item.messageId))
    let receivedNewMessage = false
    latest.items.forEach((item) => {
      if (!knownIds.has(item.messageId)) receivedNewMessage = true
      mergeMessage(messages.value, item)
    })
    chatWebSocket.updateCursor(roomId.value, {
      lastMessageSeq: maxSequence(messages.value, 'roomSeq') ?? '0',
      lastNotificationSeq: maxSequence(notifications.value, 'notificationSeq') ?? '0',
    })
    if (receivedNewMessage) await scrollToLatest()
  } catch { /* The next scheduled reconciliation retries without interrupting chat. */ }
  finally { reconciling = false }
}
function reviewMessage(payload: Record<string, unknown>, event: WsEvent): void {
  const messageId = String(payload.messageId ?? '')
  if (!messageId) return
  const existing = privateMessages.value.find((item) => item.messageId === messageId)
  mergeMessage(privateMessages.value, {
    messageId, roomId: String(payload.roomId ?? roomId.value), roomSeq: typeof payload.roomSeq === 'string' ? payload.roomSeq : null, notificationSeq: null,
    senderId: '', messageType: 'CHAT', content: existing?.content ?? '', messageStatus: String(payload.messageStatus ?? 'PENDING_REVIEW') as MessageStatus,
    createdAt: existing?.createdAt ?? event.occurredAt ?? new Date().toISOString(), reviewDeadlineAt: typeof payload.reviewDeadlineAt === 'string' ? payload.reviewDeadlineAt : null,
    reviewedAt: typeof payload.reviewedAt === 'string' ? payload.reviewedAt : null, publishedAt: typeof payload.publishedAt === 'string' ? payload.publishedAt : null,
  })
  if (event.causationRequestId) sendLoading.value = false
}
function handleEvent(event: WsEvent): void {
  const payload = event.payload as Record<string, unknown>
  if (event.type === 'CHAT_MESSAGE' && payload.roomId === roomId.value) {
    mergeMessage(messages.value, payload as unknown as ChatMessage)
    const selfStatus = statusById.value.get(String(payload.messageId))
    if (selfStatus) mergeMessage(privateMessages.value, { ...selfStatus, messageStatus: 'PUBLISHED', publishedAt: String(payload.publishedAt ?? new Date().toISOString()) })
    chatWebSocket.markRoomEventProcessed(event)
    void scrollToLatest()
  } else if (event.type === 'NOTIFICATION' && payload.roomId === roomId.value) {
    mergeMessage(notifications.value, payload as unknown as ChatMessage)
    chatWebSocket.markRoomEventProcessed(event)
  } else if (event.type === 'REVIEW_STATUS' && payload.roomId === roomId.value) reviewMessage(payload, event)
  else if (event.type === 'SUBSCRIBE_ROOM' && payload.roomId === roomId.value) {
    realtimeSubscribed.value = payload.subscriptionStatus === 'SUBSCRIBED' || payload.subscriptionStatus === 'SUBSCRIBED_WITH_GAP'
    if (payload.subscriptionStatus === 'SUBSCRIBED_WITH_GAP') replayGap.value = true
  } else if (event.type === 'CONNECTION_STATUS') {
    realtimeSubscribed.value = false
  }
  else if (event.type === 'ERROR' && payload.roomId === roomId.value) {
    if (payload.commandType === 'SUBSCRIBE_ROOM') { chatWebSocket.forgetSubscription(roomId.value); messageError.value = payload.code === 'ROOM_DELETED' ? '此房间已删除，无法继续访问。' : '你已失去该房间访问权限。' }
    else if (payload.commandType === 'CHAT_SUBMIT') { sendLoading.value = false; ElMessage.error('消息未能提交，请检查内容后重试。') }
  }
}
async function loadInitial(): Promise<void> {
  loading.value = true; messageError.value = ''
  try {
    const [roomData, history] = await Promise.all([getRoom(roomId.value), listRoomMessages(roomId.value)])
    room.value = roomData; messages.value = history.items; messageCursor.value = history.nextBeforeSeq ?? undefined; messagesHasMore.value = Boolean(history.hasMore)
    realtimeSubscribed.value = false
    chatWebSocket.subscribe({ roomId: roomId.value, lastMessageSeq: maxSequence(messages.value, 'roomSeq') ?? '0', lastNotificationSeq: maxSequence(notifications.value, 'notificationSeq') ?? '0' })
    await scrollToLatest()
  } catch { messageError.value = '加载聊天室失败。请返回我的聊天室后重试。' } finally { loading.value = false }
}
async function loadOlder(): Promise<void> {
  if (!messageCursor.value) return
  olderLoading.value = true
  try { const page = await listRoomMessages(roomId.value, messageCursor.value); page.items.forEach((item) => mergeMessage(messages.value, item)); messageCursor.value = page.nextBeforeSeq ?? undefined; messagesHasMore.value = Boolean(page.hasMore) } finally { olderLoading.value = false }
}
async function submit(): Promise<void> {
  const content = draft.value.trim()
  if (!content || sendLoading.value || !canSend.value) return
  if (Array.from(content).length > 320) { ElMessage.warning('单条消息不能超过 320 个字符。'); return }
  sendLoading.value = true
  try { chatWebSocket.send('CHAT_SUBMIT', { roomId: roomId.value, content }); draft.value = '' } catch { sendLoading.value = false; ElMessage.error('实时连接未就绪，请等待重连后重试。') }
}
async function openMembers(): Promise<void> { memberOpen.value = true; membersLoading.value = true; try { members.value = (await listRoomMembers(roomId.value)).items } finally { membersLoading.value = false } }
async function openNotifications(): Promise<void> { notificationOpen.value = true; if (notifications.value.length === 0) notifications.value = (await listRoomNotifications(roomId.value)).items }
async function exitRoom(): Promise<void> { try { await ElMessageBox.confirm('退出后将停止实时订阅；之后可以重新申请加入。', '退出聊天室？', { confirmButtonText: '退出聊天室', confirmButtonClass: 'el-button--danger', cancelButtonText: '取消', type: 'warning' }); await leaveRoom(roomId.value); chatWebSocket.unsubscribe(roomId.value); await router.replace({ name: 'my-rooms' }); ElMessage.success('已退出聊天室。') } catch { /* user cancelled */ } }
async function scrollToLatest(): Promise<void> { await nextTick(); document.querySelector('.message-stream')?.scrollTo({ top: 999999, behavior: 'smooth' }) }
onMounted(() => {
  removeListener = chatWebSocket.on(handleEvent)
  void loadInitial()
  reconcileTimer = window.setInterval(() => { void reconcileRecentMessages() }, 2_000)
})
onBeforeUnmount(() => {
  removeListener?.()
  if (reconcileTimer !== undefined) window.clearInterval(reconcileTimer)
  chatWebSocket.unsubscribe(roomId.value)
})
</script>

<template>
  <section v-if="messageError" class="page-stack"><el-result icon="error" title="无法进入聊天室" :sub-title="messageError"><template #extra><el-button type="primary" @click="router.push({ name: 'my-rooms' })">返回我的聊天室</el-button><el-button @click="router.push({ name: 'my-messages' })">查看我的消息</el-button></template></el-result></section>
  <section v-else class="chat-page">
    <el-skeleton v-if="loading" :rows="12" animated />
    <template v-else-if="room">
      <header class="chat-header"><div><el-button text @click="router.push({ name: 'my-rooms' })">← 我的聊天室</el-button><h1>{{ room.name }} <el-tag :type="roomStatusType(room.roomStatus)" effect="light">{{ roomStatusText(room.roomStatus) }}</el-tag><el-tag :type="realtimeSubscribed ? 'success' : 'warning'" effect="plain">{{ realtimeSubscribed ? '实时已订阅' : '正在订阅实时消息…' }}</el-tag></h1><p>{{ room.description || '实时讨论' }}</p></div><div class="chat-actions"><el-button @click="openMembers">成员</el-button><el-button @click="openNotifications">历史通知</el-button><el-button type="danger" plain @click="exitRoom">退出聊天室</el-button></div></header>
      <el-alert v-if="replayGap" type="warning" :closable="true" title="实时连接已恢复。部分消息已超出留存期，无法补回。" />
      <el-alert v-if="room.roomStatus !== 'ACTIVE'" type="info" :closable="false" :title="`此房间${roomStatusText(room.roomStatus)}，不能发送普通消息；历史消息仍可查看。`" />
      <div v-if="notifications.length" class="emergency-stack"><article v-for="notice in notifications.slice().sort((a, b) => Number(b.notificationSeq ?? 0) - Number(a.notificationSeq ?? 0)).slice(0, 2)" :key="notice.messageId" class="emergency-notice"><strong>紧急通知</strong><SafeText :content="notice.content" /><span>{{ formatTime(notice.publishedAt ?? notice.createdAt) }}</span></article></div>
      <main class="message-stream"><div v-if="messagesHasMore" class="load-more"><el-button text :loading="olderLoading" @click="loadOlder">加载更早消息</el-button></div><el-empty v-if="publishedMessages.length === 0" description="暂无已发布消息" /><article v-for="item in publishedMessages" :key="item.messageId" class="message-item" :class="{ 'message-admin': item.messageType === 'ADMIN_MESSAGE' }"><header><strong>{{ item.senderDisplayName || (item.messageType === 'ADMIN_MESSAGE' ? '管理员' : '成员') }}</strong><span v-if="item.messageType === 'ADMIN_MESSAGE'" class="admin-label">管理员消息</span><time>{{ formatTime(item.publishedAt ?? item.createdAt) }}</time></header><SafeText :content="item.content" /></article></main>
      <section class="pending-panel" v-if="privateMessages.filter((item) => item.messageStatus !== 'PUBLISHED').length"><h2>我正在等待审核的消息</h2><article v-for="item in privateMessages.filter((item) => item.messageStatus !== 'PUBLISHED')" :key="item.messageId" class="pending-item"><SafeText :content="item.content || '消息状态已更新。'" /><el-tag :type="messageStatusType(item.messageStatus)">{{ messageStatusText(item.messageStatus) }}</el-tag><span v-if="item.messageStatus === 'PENDING_REVIEW' && item.reviewDeadlineAt">预计在 {{ formatTime(item.reviewDeadlineAt) }} 前处理</span></article></section>
      <footer class="composer"><el-input v-model="draft" type="textarea" :rows="3" :maxlength="320" :disabled="!canSend" placeholder="输入普通文本消息；提交后将进入审核。" @keydown.ctrl.enter="submit" /><div><span :class="{ overlimit: characterCount > 320 }">{{ characterCount }} / 320</span><el-button type="primary" :disabled="!draft.trim() || !canSend" :loading="sendLoading" @click="submit">提交审核</el-button></div></footer>
    </template>
    <el-drawer v-model="memberOpen" title="成员" size="360px"><el-skeleton v-if="membersLoading" :rows="8" animated /><el-empty v-else-if="members.length === 0" description="暂无可显示的成员" /><ul v-else class="drawer-list"><li v-for="member in members" :key="member.membershipId"><span>{{ member.displayName || '成员' }}</span><time>加入于 {{ formatTime(member.joinedAt) }}</time></li></ul></el-drawer>
    <el-drawer v-model="notificationOpen" title="紧急通知" size="420px"><el-empty v-if="notifications.length === 0" description="暂无紧急通知" /><div v-else class="notification-history"><article v-for="notice in notifications.slice().sort((a, b) => Number(b.notificationSeq ?? 0) - Number(a.notificationSeq ?? 0))" :key="notice.messageId" class="emergency-notice"><strong>紧急通知 · #{{ notice.notificationSeq }}</strong><SafeText :content="notice.content" /><span>{{ formatTime(notice.publishedAt ?? notice.createdAt) }}</span></article></div></el-drawer>
  </section>
</template>
