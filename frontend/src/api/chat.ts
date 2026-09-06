import { request } from '@/api/http'
import type { ChatMessage, ChatRoom, CursorPage, MemberStatus, Membership, MessageStatus, OffsetPage, RoomStatus } from '@/types/api'

function query(params: Record<string, string | number | undefined>): Record<string, string | number> {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined)) as Record<string, string | number>
}
function room(value: any): ChatRoom { return { ...value, roomId: value.roomId ?? value.id, roomStatus: value.roomStatus ?? value.status, activeMemberCount: value.activeMemberCount ?? 0 } }
function membership(value: any): Membership { return { ...value, membershipId: value.membershipId ?? value.id, memberStatus: value.memberStatus ?? value.status, createdAt: value.createdAt ?? value.requestedAt, joinedAt: value.joinedAt ?? value.activatedAt, room: value.room ? room(value.room) : undefined } }
function message(value: any): ChatMessage { return { ...value, messageId: value.messageId ?? value.id, messageStatus: value.messageStatus ?? value.status } }
function roomPage(value: any): OffsetPage<ChatRoom> { return { ...value, items: value.items.map(room) } }
function memberPage(value: any): OffsetPage<Membership> { return { ...value, items: value.items.map(membership) } }
function messagePage(value: any): CursorPage<ChatMessage> { return { ...value, items: value.items.map(message) } }

export async function listRooms(filters: { name?: string; roomStatus?: RoomStatus; joinMode?: string; page?: number; size?: number }) {
  return roomPage(await request<any>({ method: 'get', url: '/rooms', params: query(filters) }))
}

export async function getRoom(roomId: string) {
  return room(await request<any>({ method: 'get', url: `/rooms/${roomId}` }))
}

export async function joinRoom(roomId: string) {
  const value = await request<any>({ method: 'post', url: `/rooms/${roomId}/memberships`, data: {} }); return { membership: membership(value), joinResult: value.status === 'ACTIVE' ? 'JOINED' as const : 'PENDING_APPROVAL' as const }
}

export function leaveRoom(roomId: string) {
  return request<{ membershipId: string; memberStatus: 'EXITED'; leftAt: string }>({ method: 'post', url: `/rooms/${roomId}/leave`, data: {} })
}

export async function listMyRooms(filters: { memberStatus?: MemberStatus; page?: number; size?: number }) {
  return memberPage(await request<any>({ method: 'get', url: '/users/me/rooms', params: query(filters) }))
}

export async function listRoomMessages(roomId: string, beforeSeq?: string | number) {
  return messagePage(await request<any>({ method: 'get', url: `/rooms/${roomId}/messages`, params: query({ beforeSeq, limit: 50 }) }))
}

export async function listRoomNotifications(roomId: string, beforeSeq?: string | number) {
  return messagePage(await request<any>({ method: 'get', url: `/rooms/${roomId}/notifications`, params: query({ beforeSeq, limit: 50 }) }))
}

export async function listRoomMembers(roomId: string, beforeSeq?: string | number) {
  const value = await request<any>({ method: 'get', url: `/rooms/${roomId}/members`, params: query({ beforeSeq, limit: 50, memberStatus: 'ACTIVE' }) })
  return { ...value, items: value.items.map(membership) } as CursorPage<Membership>
}

export function listMyMessages(filters: { roomId?: string; messageStatus?: MessageStatus; cursor?: string }) {
  return request<CursorPage<ChatMessage>>({ method: 'get', url: '/users/me/messages', params: query(filters) })
}
