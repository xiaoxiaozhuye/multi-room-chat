import type { RouteComponent, RouteRecordRaw } from 'vue-router'
import type { UserRole } from '@/types/api'

export interface AppRouteMeta {
  title: string
  requiresAuth?: boolean
  roles?: UserRole[]
  menu?: boolean
  section?: '用户端' | '聊天室管理' | '系统管理'
}

declare module 'vue-router' {
  interface RouteMeta extends AppRouteMeta {}
}

export type AppRouteRecord = Omit<RouteRecordRaw, 'meta' | 'children' | 'component'> & {
  component?: RouteComponent
  meta: AppRouteMeta
  children?: AppRouteRecord[]
}

const WorkbenchView = () => import('@/views/admin/WorkbenchView.vue')
const AdminRoomsView = () => import('@/views/admin/AdminRoomsView.vue')
const JoinRequestsView = () => import('@/views/admin/JoinRequestsView.vue')
const ReviewsView = () => import('@/views/admin/ReviewsView.vue')
const PublishView = () => import('@/views/admin/PublishView.vue')
const AuthorizationsView = () => import('@/views/admin/AuthorizationsView.vue')
const AuditView = () => import('@/views/admin/AuditView.vue')
const MetricsView = () => import('@/views/admin/MetricsView.vue')

export const appRoutes: AppRouteRecord[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
  {
    path: '/', component: () => import('@/layouts/AppLayout.vue'), meta: { title: '工作区', requiresAuth: true }, redirect: '/rooms', children: [
      { path: 'rooms', name: 'rooms', component: () => import('@/views/RoomsView.vue'), meta: { title: '发现聊天室', requiresAuth: true, menu: true, section: '用户端' } },
      { path: 'my-rooms', name: 'my-rooms', component: () => import('@/views/MyRoomsView.vue'), meta: { title: '我的聊天室', requiresAuth: true, menu: true, section: '用户端' } },
      { path: 'my-messages', name: 'my-messages', component: () => import('@/views/MyMessagesView.vue'), meta: { title: '我的消息', requiresAuth: true, menu: true, section: '用户端' } },
      { path: 'rooms/:roomId', name: 'room-detail', component: () => import('@/views/RoomDetailView.vue'), meta: { title: '聊天室', requiresAuth: true } },
      { path: 'chat/:roomId', name: 'chat', component: () => import('@/views/ChatView.vue'), meta: { title: '聊天室会话', requiresAuth: true } },
      { path: 'admin/workbench', name: 'admin-workbench', component: WorkbenchView, meta: { title: '管理工作台', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '聊天室管理' } },
      { path: 'admin/rooms', name: 'admin-rooms', component: AdminRoomsView, meta: { title: '聊天室运营', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '聊天室管理' } },
      { path: 'admin/join-requests', name: 'admin-join-requests', component: JoinRequestsView, meta: { title: '加入审批', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '聊天室管理' } },
      { path: 'admin/review-messages', name: 'admin-review-messages', component: ReviewsView, meta: { title: '内容审核', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '聊天室管理' } },
      { path: 'admin/broadcasts', name: 'admin-broadcasts', component: PublishView, meta: { title: '广播与通知', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '聊天室管理' } },
      { path: 'admin/authorizations', name: 'admin-authorizations', component: AuthorizationsView, meta: { title: '管理员授权', requiresAuth: true, roles: ['SYSTEM_ADMIN'], menu: true, section: '系统管理' } },
      { path: 'admin/audits', name: 'admin-audits', component: AuditView, meta: { title: '审计日志', requiresAuth: true, roles: ['SYSTEM_ADMIN'], menu: true, section: '系统管理' } },
      { path: 'admin/metrics', name: 'admin-metrics', component: MetricsView, meta: { title: '运行状态', requiresAuth: true, roles: ['SYSTEM_ADMIN'], menu: true, section: '系统管理' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/rooms', meta: { title: '未找到' } },
]
