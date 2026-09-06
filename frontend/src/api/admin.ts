import { request } from '@/api/http'
import type { AdminRoom, AuditItem, DeliveryResult, JoinRequest, Page, ReviewMessage, RoomAuthorization, SystemMetrics } from '@/types/api'

type Params = Record<string, string | number | undefined>
const query = (params: Params) => Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== ''))

export const adminApi = {
  rooms: (params: Params = {}) => {
    const { status, ...rest } = params
    return request<Page<AdminRoom>>({ method: 'get', url: '/admin/rooms', params: query({ ...rest, roomStatus: status }) })
  },
  createRoom: (data: Pick<AdminRoom, 'name' | 'description' | 'maxMembers' | 'joinMode'>) => request<AdminRoom>({ method: 'post', url: '/admin/rooms', data }),
  updateRoom: (roomId: string, data: Partial<Pick<AdminRoom, 'name' | 'description' | 'maxMembers' | 'joinMode' | 'status'>>) => request<AdminRoom>({ method: 'patch', url: `/admin/rooms/${roomId}`, data }),
  deleteRoom: (roomId: string) => request({ method: 'delete', url: `/admin/rooms/${roomId}` }),
  joinRequests: (params: Params = {}) => request<Page<JoinRequest>>({ method: 'get', url: '/admin/join-requests', params: query(params) }),
  approveJoin: (id: string) => request<JoinRequest>({ method: 'post', url: `/admin/join-requests/${id}/approve` }),
  rejectJoin: (id: string) => request<JoinRequest>({ method: 'post', url: `/admin/join-requests/${id}/reject` }),
  reviews: (params: Params = {}) => request<Page<ReviewMessage>>({ method: 'get', url: '/admin/review-messages', params: query(params) }),
  review: (id: string, action: 'approve' | 'reject') => request<ReviewMessage>({ method: 'post', url: `/admin/review-messages/${id}/${action}` }),
  batchReview: (messageIds: string[], action: 'APPROVE' | 'REJECT') => request<{ results: Array<{ messageId: string; reviewResult: string; messageStatus?: string; errorCode?: string }> }>({ method: 'post', url: '/admin/review-messages/batch', data: { messageIds, action } }),
  publish: (emergency: boolean, roomIds: string[], content: string) => request<{ broadcastId?: string; notificationId?: string; results: DeliveryResult[] }>({ method: 'post', url: emergency ? '/admin/emergency-notifications' : '/admin/broadcasts', data: { roomIds, content } }),
  authorizations: (roomId: string) => request<RoomAuthorization[]>({ method: 'get', url: `/admin/rooms/${roomId}/authorizations` }),
  grant: (roomId: string, userId: string) => request({ method: 'put', url: `/admin/rooms/${roomId}/authorizations/${userId}` }),
  revoke: (roomId: string, userId: string) => request({ method: 'delete', url: `/admin/rooms/${roomId}/authorizations/${userId}` }),
  audits: (params: Params = {}) => request<Page<AuditItem>>({ method: 'get', url: '/admin/audits', params: query(params) }),
  metrics: (window: 'CURRENT' | 'TODAY') => request<SystemMetrics>({ method: 'get', url: '/admin/system/metrics', params: { window } }),
}
