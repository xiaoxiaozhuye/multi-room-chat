<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import { listRoomMembers, listRoomMessages, listRoomNotifications, getRoom, getRoomModeration, leaveRoom } from '@/api/chat'
import SafeText from '@/components/SafeText.vue'
import { chatWebSocket } from '@/websocket/client'
import type { ChatMessage, ChatRoom, Membership, MessageStatus } from '@/types/api'
import type { WsEvent } from '@/websocket/protocol'
import { formatTime, messageStatusText, messageStatusType, roomStatusText } from '@/utils/presentation'
import { useAuthStore } from '@/stores/auth'

const route = useRoute(); const router = useRouter()
const auth = useAuthStore()
const roomId = computed(() => String(route.params.roomId))
const room = ref<ChatRoom>(); const messages = ref<ChatMessage[]>([]); const privateMessages = ref<ChatMessage[]>([]); const notifications = ref<ChatMessage[]>([])
const moderationEnabled = ref(true)
const draft = ref(''); const loading = ref(true); const olderLoading = ref(false); const sendLoading = ref(false); const messageError = ref(''); const replayGap = ref(false); const realtimeSubscribed = ref(false)
const messageCursor = ref<string | number | undefined>(); const messagesHasMore = ref(false); const memberOpen = ref(false); const notificationOpen = ref(false); const members = ref<Membership[]>([]); const membersLoading = ref(false)
const hiddenNotificationIds = ref<Set<string>>(new Set())
const recalledMessageIds = ref<Set<string>>(new Set())
const locallyDeletedMessageIds = ref<Set<string>>(new Set())
const messageActionItem = ref<ChatMessage | null>(null)
const messageStream = ref<HTMLElement | null>(null)
let removeListener: (() => void) | undefined
let reconcileTimer: number | undefined
let longPressTimer: number | undefined
let reconciling = false
const notificationTimers = new Map<string, number>()
function seenNotificationStorageKey(): string { return `chat-room:${roomId.value}:seen-emergency-notifications` }
function readSeenNotificationIds(): Set<string> {
  try {
    const raw = localStorage.getItem(seenNotificationStorageKey())
    const ids = raw ? JSON.parse(raw) : []
    return new Set(Array.isArray(ids) ? ids.filter((id): id is string => typeof id === 'string') : [])
  } catch { return new Set() }
}
function markNotificationSeen(messageId: string): void {
  try {
    const ids = readSeenNotificationIds()
    ids.add(messageId)
    localStorage.setItem(seenNotificationStorageKey(), JSON.stringify([...ids].slice(-200)))
  } catch { /* Storage may be unavailable; the in-memory timer still handles this visit. */ }
}

function localMessageStorageKey(kind: 'recalled' | 'deleted'): string {
  return `chat-room:${roomId.value}:user:${auth.user?.userId ?? 'anonymous'}:${kind}-messages`
}
function lastReadStorageKey(): string {
  return `chat-room:${roomId.value}:last-read-seq`
}
function markMessagesRead(): void {
  const latest = maxSequence(messages.value, 'roomSeq')
  if (!latest) return
  try { localStorage.setItem(lastReadStorageKey(), latest) } catch { /* Storage may be unavailable. */ }
}
function readMessageIds(kind: 'recalled' | 'deleted'): Set<string> {
  try {
    const raw = localStorage.getItem(localMessageStorageKey(kind))
    const ids = raw ? JSON.parse(raw) : []
    return new Set(Array.isArray(ids) ? ids.filter((id): id is string => typeof id === 'string') : [])
  } catch { return new Set() }
}
function persistMessageIds(kind: 'recalled' | 'deleted', ids: Set<string>): void {
  try { localStorage.setItem(localMessageStorageKey(kind), JSON.stringify([...ids].slice(-500))) } catch { /* Storage may be unavailable. */ }
}

const publishedMessages = computed(() => messages.value
  .filter((item) => !locallyDeletedMessageIds.value.has(item.messageId))
  .sort((a, b) => Number(a.roomSeq ?? 0) - Number(b.roomSeq ?? 0)))
const memberNameByUserId = computed(() => new Map(members.value.map((member) => [member.userId, member.displayName || ''])))
const memberByUserId = computed(() => new Map(members.value.map((member) => [member.userId, member])))
const canSend = computed(() => room.value?.roomStatus === 'ACTIVE')
const statusById = computed(() => new Map(privateMessages.value.map((item) => [item.messageId, item])))
function isOwnMessage(item: ChatMessage): boolean { return item.senderId === auth.user?.userId }
function startMessagePress(item: ChatMessage, event: PointerEvent): void {
  if (!isOwnMessage(item) || recalledMessageIds.value.has(item.messageId)) return
  cancelMessagePress()
  longPressTimer = window.setTimeout(() => {
    messageActionItem.value = item
    longPressTimer = undefined
  }, 550)
  if (event.pointerType === 'touch') event.preventDefault()
}
function cancelMessagePress(): void {
  if (longPressTimer !== undefined) window.clearTimeout(longPressTimer)
  longPressTimer = undefined
}
function openMessageActions(item: ChatMessage): void {
  cancelMessagePress()
  if (isOwnMessage(item) && !recalledMessageIds.value.has(item.messageId)) messageActionItem.value = item
}
function closeMessageActions(): void { messageActionItem.value = null }
function handleOutsideMessagePress(event: PointerEvent): void {
  const activeId = messageActionItem.value?.messageId
  if (!activeId) return
  const target = event.target
  if (!(target instanceof Element)) { closeMessageActions(); return }
  const currentMessage = target.closest('.message-item')
  if (currentMessage?.getAttribute('data-message-id') !== activeId) closeMessageActions()
}
function senderName(item: ChatMessage): string {
  return item.senderDisplayName || memberNameByUserId.value.get(item.senderId) || (item.messageType === 'ADMIN_MESSAGE' ? '管理员' : '成员')
}
function senderAvatar(item: ChatMessage): string | undefined {
  return isOwnMessage(item) ? auth.user?.avatarUrl : memberByUserId.value.get(item.senderId)?.avatarUrl
}
function messageTime(item: ChatMessage): number { return Date.parse(item.publishedAt ?? item.createdAt) }
function shouldShowMessageTime(index: number): boolean {
  if (index === 0) return true
  return messageTime(publishedMessages.value[index]) - messageTime(publishedMessages.value[index - 1]) > 3 * 60 * 1000
}
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
function dismissNotification(messageId: string): void {
  markNotificationSeen(messageId)
  const timer = notificationTimers.get(messageId)
  if (timer !== undefined) {
    window.clearTimeout(timer)
    notificationTimers.delete(messageId)
  }
  const next = new Set(hiddenNotificationIds.value)
  next.add(messageId)
  hiddenNotificationIds.value = next
}
function showNewNotification(messageId: string): void {
  const previousTimer = notificationTimers.get(messageId)
  if (previousTimer !== undefined) window.clearTimeout(previousTimer)
  notificationTimers.set(messageId, window.setTimeout(() => {
    dismissNotification(messageId)
  }, 15_000))
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
    markMessagesRead()
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
    markMessagesRead()
    void scrollToLatest()
  } else if (event.type === 'NOTIFICATION' && payload.roomId === roomId.value) {
    const notification = payload as unknown as ChatMessage
    const isNewNotification = !notifications.value.some((item) => item.messageId === notification.messageId)
    mergeMessage(notifications.value, notification)
    if (isNewNotification && !readSeenNotificationIds().has(notification.messageId)) {
      markNotificationSeen(notification.messageId)
      showNewNotification(notification.messageId)
    }
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
    const [roomData, history, moderation, notificationHistory, memberPage] = await Promise.all([
      getRoom(roomId.value), listRoomMessages(roomId.value), getRoomModeration(roomId.value).catch(() => ({ enabled: true })), listRoomNotifications(roomId.value), listRoomMembers(roomId.value),
    ])
    room.value = roomData; route.meta.title = roomData.name; messages.value = history.items; messageCursor.value = history.nextBeforeSeq ?? undefined; messagesHasMore.value = Boolean(history.hasMore)
    markMessagesRead()
    recalledMessageIds.value = readMessageIds('recalled')
    locallyDeletedMessageIds.value = readMessageIds('deleted')
    members.value = memberPage.items
    notifications.value = notificationHistory.items
    notificationHistory.items.forEach((item) => markNotificationSeen(item.messageId))
    hiddenNotificationIds.value = new Set(notificationHistory.items.map((item) => item.messageId))
    moderationEnabled.value = moderation.enabled
    realtimeSubscribed.value = false
    chatWebSocket.subscribe({ roomId: roomId.value, lastMessageSeq: maxSequence(messages.value, 'roomSeq') ?? '0', lastNotificationSeq: maxSequence(notifications.value, 'notificationSeq') ?? '0' })
  } catch { messageError.value = '加载聊天室失败。请返回我的聊天室后重试。' } finally {
    loading.value = false
    if (!messageError.value) await scrollToLatest('auto')
  }
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
async function scrollToLatest(behavior: ScrollBehavior = 'smooth'): Promise<void> {
  await nextTick()
  const stream = messageStream.value
  if (!stream) return
  if (behavior === 'auto') {
    stream.scrollTop = stream.scrollHeight
    return
  }
  stream.scrollTo({ top: stream.scrollHeight, behavior })
}
async function deleteLocalMessage(item: ChatMessage): Promise<void> {
  closeMessageActions()
  try {
    await ElMessageBox.confirm('删除后只会从你的当前设备隐藏，其他成员仍可看到这条消息。', '删除本地消息？', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  const next = new Set(locallyDeletedMessageIds.value)
  next.add(item.messageId)
  locallyDeletedMessageIds.value = next
  persistMessageIds('deleted', next)
  ElMessage.success('已从本机删除。')
}
onMounted(() => {
  removeListener = chatWebSocket.on(handleEvent)
  document.addEventListener('pointerdown', handleOutsideMessagePress)
  void loadInitial()
  reconcileTimer = window.setInterval(() => { void reconcileRecentMessages() }, 2_000)
})
onBeforeUnmount(() => {
  removeListener?.()
  document.removeEventListener('pointerdown', handleOutsideMessagePress)
  if (reconcileTimer !== undefined) window.clearInterval(reconcileTimer)
  cancelMessagePress()
  notificationTimers.forEach((timer) => window.clearTimeout(timer))
  notificationTimers.clear()
  chatWebSocket.unsubscribe(roomId.value)
  route.meta.title = '聊天室会话'
})
</script>

<template>
  <section v-if="messageError" class="page-stack"><el-result icon="error" title="无法进入聊天室" :sub-title="messageError"><template #extra><el-button type="primary" @click="router.push({ name: 'my-rooms' })">返回我的聊天室</el-button><el-button @click="router.push({ name: 'my-messages' })">查看我的消息</el-button></template></el-result></section>
  <section v-else class="chat-page" :class="{ 'composer-has-text': draft.trim().length > 0 }">
    <el-skeleton v-if="loading" :rows="12" animated />
    <template v-else-if="room">
      <header class="chat-header"><div class="chat-actions"><el-dropdown placement="bottom-end"><el-button :icon="MoreFilled" circle aria-label="更多操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item @click="openMembers">成员（{{ room.activeMemberCount }}）</el-dropdown-item><el-dropdown-item @click="openNotifications">历史通知</el-dropdown-item><el-dropdown-item divided class="danger-menu-item" @click="exitRoom">退出聊天室</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></header>
      <el-alert v-if="replayGap" type="warning" :closable="true" title="实时连接已恢复。部分消息已超出留存期，无法补回。" />
      <el-alert v-if="room.roomStatus !== 'ACTIVE'" type="info" :closable="false" :title="`此房间${roomStatusText(room.roomStatus)}，不能发送普通消息；历史消息仍可查看。`" />
      <div v-if="notifications.some((notice) => !hiddenNotificationIds.has(notice.messageId))" class="emergency-stack" aria-live="assertive"><article v-for="notice in notifications.filter((item) => !hiddenNotificationIds.has(item.messageId)).slice().sort((a, b) => Number(b.notificationSeq ?? 0) - Number(a.notificationSeq ?? 0)).slice(0, 2)" :key="notice.messageId" class="emergency-notice"><div class="notice-toggle"><span class="notice-icon" aria-hidden="true">⚠</span><strong>紧急通知</strong><button type="button" class="notice-close" aria-label="关闭紧急通知" @click="dismissNotification(notice.messageId)">×</button></div><div class="notice-details"><SafeText :content="notice.content" /><span>{{ formatTime(notice.publishedAt ?? notice.createdAt) }}</span></div></article></div>
      <main ref="messageStream" class="message-stream"><div v-if="messagesHasMore" class="load-more"><el-button text :loading="olderLoading" @click="loadOlder">加载更早消息</el-button></div><el-empty v-if="publishedMessages.length === 0" description="暂无已发布消息" /><template v-for="(item, index) in publishedMessages" :key="item.messageId"><div v-if="shouldShowMessageTime(index)" class="message-time-divider"><span>{{ formatTime(item.publishedAt ?? item.createdAt) }}</span></div><article :data-message-id="item.messageId" class="message-item" :class="{ 'message-admin': item.messageType === 'ADMIN_MESSAGE', 'message-self': isOwnMessage(item), 'message-recalled': recalledMessageIds.has(item.messageId) }" @pointerdown="startMessagePress(item, $event)" @pointerup="cancelMessagePress" @pointerleave="cancelMessagePress" @pointercancel="cancelMessagePress" @contextmenu.prevent="openMessageActions(item)"><el-avatar class="message-avatar" :size="38" :src="senderAvatar(item)">{{ isOwnMessage(item) ? (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1) : senderName(item).slice(0, 1) }}</el-avatar><div class="message-bubble"><header v-if="!isOwnMessage(item)"><strong>{{ senderName(item) }}</strong><span v-if="item.messageType === 'ADMIN_MESSAGE'" class="admin-label">管理员消息</span></header><span v-if="recalledMessageIds.has(item.messageId)" class="recalled-copy">你撤回了一条消息</span><SafeText v-else :content="item.content" /><div v-if="messageActionItem?.messageId === item.messageId" class="message-inline-actions" @pointerdown.stop><el-button text @click.stop="deleteLocalMessage(item)">删除</el-button></div></div></article></template></main>
      <section class="pending-panel" v-if="privateMessages.filter((item) => item.messageStatus !== 'PUBLISHED').length"><h2>我正在等待审核的消息</h2><article v-for="item in privateMessages.filter((item) => item.messageStatus !== 'PUBLISHED')" :key="item.messageId" class="pending-item"><SafeText :content="item.content || '消息状态已更新。'" /><el-tag :type="messageStatusType(item.messageStatus)">{{ messageStatusText(item.messageStatus) }}</el-tag><span v-if="item.messageStatus === 'PENDING_REVIEW' && item.reviewDeadlineAt">预计在 {{ formatTime(item.reviewDeadlineAt) }} 前处理</span></article></section>
      <footer class="composer"><div class="composer-row"><el-input v-model="draft" type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" :maxlength="320" :disabled="!canSend" placeholder="输入消息…" @keydown.ctrl.enter="submit" /><el-button class="composer-send" type="primary" :disabled="!draft.trim() || !canSend" :loading="sendLoading" @click="submit">发送</el-button></div></footer>
    </template>
    <el-drawer v-model="memberOpen" :title="`成员（${room?.activeMemberCount ?? members.length}）`" size="360px"><el-skeleton v-if="membersLoading" :rows="8" animated /><el-empty v-else-if="members.length === 0" description="暂无可显示的成员" /><ul v-else class="drawer-list"><li v-for="member in members" :key="member.membershipId"><span class="member-identity"><el-avatar :size="36" :src="member.avatarUrl || undefined">{{ (member.displayName || '成员').slice(0, 1) }}</el-avatar><span><strong>{{ member.displayName || '成员' }}</strong><small>Lv.{{ member.level || 1 }}</small></span></span><time>加入于 {{ formatTime(member.joinedAt) }}</time></li></ul></el-drawer>
    <el-drawer v-model="notificationOpen" title="紧急通知" size="420px"><el-empty v-if="notifications.length === 0" description="暂无紧急通知" /><div v-else class="notification-history"><article v-for="notice in notifications.slice().sort((a, b) => Number(b.notificationSeq ?? 0) - Number(a.notificationSeq ?? 0))" :key="notice.messageId" class="emergency-notice"><strong>紧急通知 · #{{ notice.notificationSeq }}</strong><SafeText :content="notice.content" /><span>{{ formatTime(notice.publishedAt ?? notice.createdAt) }}</span></article></div></el-drawer>
  </section>
</template>
