<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listMyRooms, listRoomMessages } from '@/api/chat'
import type { ChatMessage, Membership } from '@/types/api'
import { formatTime } from '@/utils/presentation'
import { useAuthStore } from '@/stores/auth'
import { chatWebSocket } from '@/websocket/client'
import type { WsEvent } from '@/websocket/protocol'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const memberships = ref<Membership[]>([])
const latestByRoom = ref<Record<string, { message?: ChatMessage; total: string; unread: number }>>({})
const messageIdsByRoom = new Map<string, Set<string>>()
const lastMessageSeqByRoom = new Map<string, string>()
const subscribedRoomIds = new Set<string>()
let removeWebSocketListener: (() => void) | undefined
function readKey(roomId: string): string { return `chat-room:${roomId}:last-read-seq` }
function lastReadSequence(roomId: string): bigint { try { return BigInt(localStorage.getItem(readKey(roomId)) ?? '0') } catch { return 0n } }
function markRead(roomId: string, message?: ChatMessage): void { if (message?.roomSeq) localStorage.setItem(readKey(roomId), message.roomSeq) }
function isOwnMessage(message: ChatMessage): boolean { return message.senderId === auth.user?.userId }
function deletedMessageKey(roomId: string): string { return `chat-room:${roomId}:user:${auth.user?.userId ?? 'anonymous'}:deleted-messages` }
function readDeletedMessageIds(roomId: string): Set<string> {
  try {
    const raw = localStorage.getItem(deletedMessageKey(roomId))
    const ids = raw ? JSON.parse(raw) : []
    return new Set(Array.isArray(ids) ? ids.filter((id): id is string => typeof id === 'string') : [])
  } catch { return new Set() }
}
function maxSequence(items: ChatMessage[]): string | undefined {
  return items.reduce<string | undefined>((max, item) => {
    const value = item.roomSeq
    return value !== null && (!max || BigInt(value) > BigInt(max)) ? value : max
  }, undefined)
}
function updateLiveMessage(message: ChatMessage): void {
  const roomId = message.roomId
  if (!roomId || readDeletedMessageIds(roomId).has(message.messageId)) return

  const knownIds = messageIdsByRoom.get(roomId) ?? new Set<string>()
  if (knownIds.has(message.messageId)) return
  knownIds.add(message.messageId)
  messageIdsByRoom.set(roomId, knownIds)

  const current = latestByRoom.value[roomId] ?? { total: '0', unread: 0 }
  const readSeq = lastReadSequence(roomId)
  const isUnread = !isOwnMessage(message) && Boolean(message.roomSeq && BigInt(message.roomSeq) > readSeq)
  const previousTotal = Number.parseInt(current.total, 10)
  latestByRoom.value = {
    ...latestByRoom.value,
    [roomId]: {
      message: !current.message || Date.parse(message.publishedAt ?? message.createdAt) >= Date.parse(current.message.publishedAt ?? current.message.createdAt) ? message : current.message,
      total: Number.isFinite(previousTotal) ? String(previousTotal + 1) : current.total,
      unread: current.unread + (isUnread ? 1 : 0),
    },
  }
}
function handleWebSocketEvent(event: WsEvent): void {
  if (event.type !== 'CHAT_MESSAGE') return
  const payload = event.payload as Partial<ChatMessage>
  if (typeof payload.roomId !== 'string' || typeof payload.messageId !== 'string') return
  updateLiveMessage(payload as ChatMessage)
  chatWebSocket.markRoomEventProcessed(event)
}
async function load(): Promise<void> {
  loading.value = true
  latestByRoom.value = {}
  messageIdsByRoom.clear()
  lastMessageSeqByRoom.clear()
  subscribedRoomIds.forEach((roomId) => chatWebSocket.unsubscribe(roomId))
  subscribedRoomIds.clear()
  try {
    memberships.value = (await listMyRooms({ memberStatus: 'ACTIVE', size: 50 })).items
    const activeRooms = memberships.value
    const entries = await Promise.all(activeRooms.map(async (item) => {
      try {
        const page = await listRoomMessages(item.roomId)
        const deletedIds = readDeletedMessageIds(item.roomId)
        const visibleItems = page.items.filter((entry) => !deletedIds.has(entry.messageId))
        messageIdsByRoom.set(item.roomId, new Set(visibleItems.map((entry) => entry.messageId)))
        const lastMessageSeq = maxSequence(visibleItems)
        if (lastMessageSeq) lastMessageSeqByRoom.set(item.roomId, lastMessageSeq)
        const message = visibleItems.slice().sort((a, b) => Date.parse(b.publishedAt ?? b.createdAt) - Date.parse(a.publishedAt ?? a.createdAt))[0]
        const unread = visibleItems.filter((entry) => !isOwnMessage(entry) && entry.roomSeq && BigInt(entry.roomSeq) > lastReadSequence(item.roomId)).length
        return [item.roomId, { message, total: page.hasMore ? `${visibleItems.length}+` : String(visibleItems.length), unread }] as const
      } catch {
        return [item.roomId, { total: '—', unread: 0 }] as const
      }
    }))
    latestByRoom.value = Object.fromEntries(entries)
    for (const item of activeRooms) {
      chatWebSocket.subscribe({ roomId: item.roomId, lastMessageSeq: lastMessageSeqByRoom.get(item.roomId) ?? '0' })
      subscribedRoomIds.add(item.roomId)
    }
  } finally { loading.value = false }
}
function open(item: Membership): void { const current = latest(item); markRead(item.roomId, current.message); if (item.memberStatus === 'ACTIVE') void router.push({ name: 'chat', params: { roomId: item.roomId } }) }
function latest(item: Membership): { message?: ChatMessage; total: string; unread: number } { return latestByRoom.value[item.roomId] ?? { total: '0', unread: 0 } }
function latestTime(item: Membership): string { return latest(item).message ? formatTime(latest(item).message!.publishedAt ?? latest(item).message!.createdAt) : '暂无消息' }
onMounted(() => {
  removeWebSocketListener = chatWebSocket.on(handleWebSocketEvent)
  void load()
})
onBeforeUnmount(() => {
  removeWebSocketListener?.()
  subscribedRoomIds.forEach((roomId) => chatWebSocket.unsubscribe(roomId))
  subscribedRoomIds.clear()
})
</script>

<template>
  <section class="message-page">
    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="memberships.length === 0" description="尚未加入任何聊天室"><el-button type="primary" @click="router.push({ name: 'rooms' })">去发现聊天室</el-button></el-empty>
    <div v-for="item in memberships" v-else :key="item.membershipId" class="im-session-row" @click="open(item)">
      <div class="session-avatar">{{ (item.room?.name ?? '聊').slice(0, 1) }}</div>
      <div class="session-main"><h2>{{ item.room?.name ?? '聊天室' }}</h2><p class="session-preview">{{ latest(item).message?.content || '暂无最新消息' }}</p></div>
      <div class="session-side"><span v-if="latest(item).unread" class="unread-count">{{ latest(item).unread > 99 ? '99+' : latest(item).unread }}</span><time>{{ latestTime(item) }}</time></div>
    </div>
  </section>
</template>
