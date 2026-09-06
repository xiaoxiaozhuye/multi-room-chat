import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { appRoutes } from '@/router/routes'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({ history: createWebHistory(), routes: appRoutes as unknown as RouteRecordRaw[] })

router.beforeEach((to) => {
  const auth = useAuthStore()
  const meta = to.meta
  if (meta.requiresAuth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (meta.roles && !auth.hasAnyRole(meta.roles)) {
    ElMessage.warning('当前账号没有该功能的操作入口。')
    return { name: 'rooms' }
  }
  if (to.name === 'login' && auth.isAuthenticated) return { name: 'rooms' }
  return true
})

export default router
