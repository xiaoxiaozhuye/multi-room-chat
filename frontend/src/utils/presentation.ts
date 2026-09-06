import type { MessageStatus, RoomStatus } from '@/types/api'

export function formatTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', month: 'numeric', day: 'numeric' }).format(date)
}

export function roomStatusText(status: RoomStatus): string {
  return ({ ACTIVE: '进行中', PAUSED: '已暂停', CLOSED: '已关闭', DELETED: '已删除' })[status]
}

export function messageStatusText(status: MessageStatus): string {
  return ({ PENDING_REVIEW: '审核中', APPROVED: '审核已通过，等待按序发布', PUBLISHED: '已发布', REJECTED: '未通过审核', TIMEOUT: '审核超时，未发布', CANCELLED_BY_ROOM_DELETION: '聊天室已删除，消息未发布' })[status]
}

export function roomStatusType(status: RoomStatus): 'success' | 'warning' | 'info' | 'danger' {
  const types = { ACTIVE: 'success', PAUSED: 'warning', CLOSED: 'info', DELETED: 'danger' } as const
  return types[status]
}

export function messageStatusType(status: MessageStatus): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'PENDING_REVIEW' || status === 'APPROVED') return 'warning'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}
