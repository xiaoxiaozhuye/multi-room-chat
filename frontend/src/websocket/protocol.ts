export type WebSocketEventType = 'SUBSCRIBE_ROOM' | 'UNSUBSCRIBE_ROOM' | 'CHAT_SUBMIT' | 'CHAT_MESSAGE' | 'REVIEW_STATUS' | 'NOTIFICATION' | 'ERROR'

export interface WsEvent<T = Record<string, unknown>> {
  type: WebSocketEventType | string
  requestId: string
  occurredAt?: string
  causationRequestId?: string
  payload: T
}

export interface RoomSubscription {
  roomId: string
  lastMessageSeq?: string
  lastNotificationSeq?: string
}
