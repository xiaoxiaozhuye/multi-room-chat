import type { RouteComponent, RouteRecordRaw } from 'vue-router'
import type { UserRole } from '@/types/api'

export interface AppRouteMeta {
  title: string
  requiresAuth?: boolean
  roles?: UserRole[]
  menu?: boolean
  section?: '用户端' | '房间管理' | '系统管理'
}

declare module 'vue-router' {
  interface RouteMeta extends AppRouteMeta {}
}

export type AppRouteRecord = Omit<RouteRecordRaw, 'meta' | 'children' | 'component'> & {
  component?: RouteComponent
  meta: AppRouteMeta
  children?: AppRouteRecord[]
}

const FeaturePage = () => import('@/views/FeaturePage.vue')

export const appRoutes: AppRouteRecord[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
  {
    path: '/', component: () => import('@/layouts/AppLayout.vue'), meta: { title: '工作区', requiresAuth: true }, redirect: '/rooms', children: [
      { path: 'rooms', name: 'rooms', component: FeaturePage, meta: { title: '发现聊天室', requiresAuth: true, menu: true, section: '用户端' } },
      { path: 'my-rooms', name: 'my-rooms', component: FeaturePage, meta: { title: '我的聊天室', requiresAuth: true, menu: true, section: '用户端' } },
      { path: 'my-messages', name: 'my-messages', component: FeaturePage, meta: { title: '我的消息', requiresAuth: true, menu: true, section: '用户端' } },
      { path: 'rooms/:roomId', name: 'room-detail', component: FeaturePage, meta: { title: '聊天室', requiresAuth: true } },
      { path: 'chat/:roomId', name: 'chat', component: FeaturePage, meta: { title: '聊天室会话', requiresAuth: true } },
      { path: 'admin/workbench', name: 'admin-workbench', component: FeaturePage, meta: { title: '管理工作台', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '房间管理' } },
      { path: 'admin/rooms', name: 'admin-rooms', component: FeaturePage, meta: { title: '房间运营', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '房间管理' } },
      { path: 'admin/join-requests', name: 'admin-join-requests', component: FeaturePage, meta: { title: '入群审批', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '房间管理' } },
      { path: 'admin/review-messages', name: 'admin-review-messages', component: FeaturePage, meta: { title: '内容审核', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '房间管理' } },
      { path: 'admin/broadcasts', name: 'admin-broadcasts', component: FeaturePage, meta: { title: '广播与通知', requiresAuth: true, roles: ['ROOM_ADMIN', 'SYSTEM_ADMIN'], menu: true, section: '房间管理' } },
      { path: 'admin/authorizations', name: 'admin-authorizations', component: FeaturePage, meta: { title: '管理员授权', requiresAuth: true, roles: ['SYSTEM_ADMIN'], menu: true, section: '系统管理' } },
      { path: 'admin/audits', name: 'admin-audits', component: FeaturePage, meta: { title: '审计日志', requiresAuth: true, roles: ['SYSTEM_ADMIN'], menu: true, section: '系统管理' } },
      { path: 'admin/metrics', name: 'admin-metrics', component: FeaturePage, meta: { title: '运行状态', requiresAuth: true, roles: ['SYSTEM_ADMIN'], menu: true, section: '系统管理' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/rooms', meta: { title: '未找到' } },
]
