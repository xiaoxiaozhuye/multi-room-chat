export type UserRole = 'USER' | 'ROOM_ADMIN' | 'SYSTEM_ADMIN'

export interface AuthenticatedUser {
  userId: string
  username: string
  displayName?: string
  email?: string
  roles: UserRole[]
  createdAt: string
}

export interface AuthSession {
  user: AuthenticatedUser
  accessToken: string
  refreshToken?: string
  expiresAt: string
}

export interface ApiErrorBody {
  code: string
  message: string
  details?: Array<{ field?: string; reason: string }>
}

export interface ApiEnvelope<T> {
  requestId: string
  data: T
}

export interface ApiErrorEnvelope {
  requestId?: string
  error: ApiErrorBody
}

export interface Page<T> { items: T[]; page: number; size: number; hasNext: boolean }
export interface AdminRoom { id: string; name: string; description?: string; maxMembers: number; joinMode: 'OPEN' | 'APPROVAL'; status: 'ACTIVE' | 'PAUSED' | 'CLOSED' | 'DELETED'; createdAt: string; updatedAt: string }
export interface JoinRequest { id: string; userId: string; roomId: string; status: string; requestedAt: string; activatedAt?: string; version: number }
export interface ReviewMessage { id: string; roomId: string; senderId: string; roomSeq?: number; notificationSeq?: number; messageType: string; content: string; status: string; reviewDeadlineAt?: string; createdAt: string }
export interface RoomAuthorization { authorizationId: string; roomId: string; adminUserId: string; grantedByUserId: string; createdAt: string }
export interface AuditItem { id: string; requestId?: string; actorId: string; action: string; resourceType: string; resourceId: string; roomId?: string; messageId?: string; beforeState?: Record<string, unknown>; afterState?: Record<string, unknown>; detail?: Record<string, unknown>; createdAt: string }
export interface DeliveryResult { roomId: string; deliveryStatus: 'SUCCESS' | 'NO_PERMISSION' | 'ROOM_NOT_SENDABLE' | 'PENDING_COMPENSATION'; messageId?: string; roomSeq?: number; notificationSeq?: number; messageStatus?: string; errorCode?: string }
export interface SystemMetrics { observedAt: string; webSocketConnectionCount: number; pendingReviewCount: number; reviewTimeoutCountToday: number; messageCountToday: number; averageReviewLatencyMsToday: number; pushFailureCountToday: number; httpErrorRate: number; databasePool: { active: number; idle: number; max: number } }

export type RoomStatus = 'ACTIVE' | 'PAUSED' | 'CLOSED' | 'DELETED'
export type JoinMode = 'OPEN' | 'APPROVAL'
export type MemberStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'EXITED'
export type MessageStatus = 'PENDING_REVIEW' | 'APPROVED' | 'PUBLISHED' | 'REJECTED' | 'TIMEOUT' | 'CANCELLED_BY_ROOM_DELETION'
export type MessageType = 'CHAT' | 'ADMIN_MESSAGE' | 'SYSTEM_NOTIFICATION'

export interface ChatRoom {
  roomId: string
  name: string
  description?: string
  maxMembers: number
  activeMemberCount: number
  joinMode: JoinMode
  roomStatus: RoomStatus
  createdAt: string
  updatedAt?: string
}

export interface Membership {
  membershipId: string
  roomId: string
  userId: string
  displayName?: string
  memberStatus: MemberStatus
  joinedAt?: string
  createdAt: string
  room?: ChatRoom
}

export interface ChatMessage {
  messageId: string
  roomId: string
  roomSeq: string | null
  notificationSeq: string | null
  senderId: string
  senderDisplayName?: string
  messageType: MessageType
  content: string
  messageStatus: MessageStatus
  createdAt: string
  reviewDeadlineAt?: string | null
  reviewedAt?: string | null
  publishedAt?: string | null
  roomDeleted?: boolean
  roomName?: string
}

export interface CursorPage<T> {
  items: T[]
  nextCursor?: string | null
  hasMore?: boolean
  nextBeforeSeq?: string | number | null
}

export interface OffsetPage<T> {
  items: T[]
  page: number
  size: number
  hasNext: boolean
}
