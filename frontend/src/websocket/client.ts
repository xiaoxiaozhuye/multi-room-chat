import { createRequestId } from '@/utils/request-id'
import type { RoomSubscription, WsEvent } from '@/websocket/protocol'

type EventListener = (event: WsEvent) => void
export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'closed'

export class ChatWebSocketClient {
  private socket?: WebSocket
  private retryTimer?: number
  private attempts = 0
  private manualClose = false
  private token?: string
  private listeners = new Set<EventListener>()
  private subscriptions = new Map<string, RoomSubscription>()
  private deliveredMessageIds = new Set<string>()
  private currentStatus: ConnectionStatus = 'idle'

  get status(): ConnectionStatus { return this.currentStatus }

  on(listener: EventListener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  connect(token: string): void {
    this.token = token
    this.manualClose = false
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) return
    this.open()
  }

  disconnect(): void {
    this.manualClose = true
    this.clearRetry()
    this.subscriptions.clear()
    this.deliveredMessageIds.clear()
    this.socket?.close(1000, 'client logout')
    this.socket = undefined
    this.setStatus('closed')
  }

  subscribe(subscription: RoomSubscription): void {
    this.subscriptions.set(subscription.roomId, subscription)
    // The subscription is retained locally while connecting and sent from
    // onopen, so navigating to a chat page never fails merely because the
    // socket handshake is still in progress.
    if (this.socket?.readyState === WebSocket.OPEN) this.send('SUBSCRIBE_ROOM', subscription)
  }

  unsubscribe(roomId: string): void {
    this.subscriptions.delete(roomId)
    if (this.socket?.readyState === WebSocket.OPEN) this.send('UNSUBSCRIBE_ROOM', { roomId })
  }

  /** Forget a server-revoked subscription so it is never revived on reconnect. */
  forgetSubscription(roomId: string): void {
    this.subscriptions.delete(roomId)
  }

  updateCursor(roomId: string, cursor: Omit<RoomSubscription, 'roomId'>): void {
    const previous = this.subscriptions.get(roomId)
    if (previous) this.subscriptions.set(roomId, { ...previous, ...cursor })
  }

  /**
   * Commit replay cursors only after the view has merged the event into its
   * rendered list.  This prevents reconnect recovery from skipping a frame
   * that was received but could not be displayed.
   */
  markRoomEventProcessed(event: WsEvent): void {
    if (event.type !== 'CHAT_MESSAGE' && event.type !== 'NOTIFICATION') return
    const payload = event.payload as { roomId?: unknown, roomSeq?: unknown, notificationSeq?: unknown }
    if (typeof payload.roomId !== 'string') return
    const current = this.subscriptions.get(payload.roomId)
    if (!current) return
    const field = event.type === 'CHAT_MESSAGE' ? 'lastMessageSeq' : 'lastNotificationSeq'
    const sequence = event.type === 'CHAT_MESSAGE' ? payload.roomSeq : payload.notificationSeq
    if (!this.isNonNegativeIntegerString(sequence)) return
    const previous = current[field]
    if (!previous || BigInt(sequence) > BigInt(previous)) this.subscriptions.set(payload.roomId, { ...current, [field]: sequence })
  }

  send(type: string, payload: unknown, requestId = createRequestId()): string {
    if (this.socket?.readyState !== WebSocket.OPEN) throw new Error('WebSocket 尚未连接。')
    this.socket.send(JSON.stringify({ type, requestId, occurredAt: new Date().toISOString(), payload }))
    return requestId
  }

  private open(): void {
    if (!this.token) return
    this.setStatus(this.attempts === 0 ? 'connecting' : 'reconnecting')
    // Use Vite's WebSocket proxy by default during development.  An absolute
    // localhost URL makes a deployed frontend try to connect to the visitor's
    // own machine, and also bypasses the configured development proxy.
    const endpoint = import.meta.env.VITE_WS_URL ?? '/ws/v1/chat'
    const authMode = import.meta.env.VITE_WS_AUTH_MODE ?? 'subprotocol'
    const url = authMode === 'query'
      ? this.withTokenQuery(endpoint, this.token)
      : this.toWebSocketUrl(endpoint)
    const protocols = authMode === 'subprotocol' ? ['chat.v1', `bearer.${this.token}`] : ['chat.v1']
    const socket = new WebSocket(url, protocols)
    this.socket = socket
    socket.onopen = () => {
      this.attempts = 0
      this.setStatus('connected')
      this.emit({ type: 'CONNECTION_OPEN', requestId: createRequestId(), payload: {} })
      for (const subscription of this.subscriptions.values()) this.send('SUBSCRIBE_ROOM', subscription)
    }
    socket.onmessage = (message) => this.receive(message.data)
    socket.onerror = () => this.emit({ type: 'CONNECTION_ERROR', requestId: createRequestId(), payload: { message: '实时连接发生错误。' } })
    socket.onclose = (event) => this.closed(event)
  }

  private receive(data: unknown): void {
    try {
      const event = JSON.parse(String(data)) as WsEvent
      if (this.isDuplicatePublishedMessage(event)) return
      this.emit(event)
    }
    catch { this.emit({ type: 'ERROR', requestId: createRequestId(), payload: { code: 'INVALID_EVENT', message: '收到无法识别的实时消息。' } }) }
  }

  private isNonNegativeIntegerString(value: unknown): value is string {
    return typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value)
  }

  /** The server intentionally uses at-least-once delivery for room events. */
  private isDuplicatePublishedMessage(event: WsEvent): boolean {
    if (event.type !== 'CHAT_MESSAGE' && event.type !== 'NOTIFICATION') return false
    const messageId = (event.payload as { messageId?: unknown })?.messageId
    if (typeof messageId !== 'string' || !messageId) return false
    if (this.deliveredMessageIds.has(messageId)) return true
    this.deliveredMessageIds.add(messageId)
    // Bound memory while retaining a comfortably large reconnect/retry window.
    if (this.deliveredMessageIds.size > 10_000) {
      const oldest = this.deliveredMessageIds.values().next().value
      if (oldest) this.deliveredMessageIds.delete(oldest)
    }
    return false
  }

  private closed(event: CloseEvent): void {
    if (this.socket?.readyState === WebSocket.CLOSED) this.socket = undefined
    this.emit({ type: 'CONNECTION_CLOSE', requestId: createRequestId(), payload: { code: event.code, reason: event.reason } })
    if (this.manualClose || event.code === 4001) {
      this.setStatus('closed')
      return
    }
    if (event.code === 4003) {
      this.setStatus('closed')
      this.emit({ type: 'AUTH_EXPIRED', requestId: createRequestId(), payload: {} })
      return
    }
    this.scheduleReconnect()
  }

  private scheduleReconnect(): void {
    this.clearRetry()
    const delay = Math.min(30_000, 1_000 * 2 ** this.attempts) + Math.floor(Math.random() * 500)
    this.attempts += 1
    this.setStatus('reconnecting')
    this.retryTimer = window.setTimeout(() => this.open(), delay)
  }

  private withTokenQuery(endpoint: string, token: string): string {
    const url = new URL(this.toWebSocketUrl(endpoint))
    url.searchParams.set('token', token)
    return url.toString()
  }

  /** Resolve relative endpoints and prevent an HTTPS page from downgrading to ws://. */
  private toWebSocketUrl(endpoint: string): string {
    const url = new URL(endpoint, location.origin)
    if (url.protocol === 'http:') url.protocol = 'ws:'
    else if (url.protocol === 'https:') url.protocol = 'wss:'
    if (url.protocol !== 'ws:' && url.protocol !== 'wss:') {
      throw new Error('VITE_WS_URL 必须使用 ws、wss、http、https 或相对路径。')
    }
    return url.toString()
  }

  private clearRetry(): void {
    if (this.retryTimer) window.clearTimeout(this.retryTimer)
    this.retryTimer = undefined
  }

  private setStatus(status: ConnectionStatus): void {
    if (status === this.currentStatus) return
    this.currentStatus = status
    this.emit({ type: 'CONNECTION_STATUS', requestId: createRequestId(), payload: { status } })
  }

  private emit(event: WsEvent): void { this.listeners.forEach((listener) => listener(event)) }
}

export const chatWebSocket = new ChatWebSocketClient()
