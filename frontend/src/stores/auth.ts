import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { AuthSession, AuthenticatedUser, UserRole } from '@/types/api'
import { clearSession, readSession, saveSession } from '@/utils/token-storage'
import { chatWebSocket } from '@/websocket/client'

export const useAuthStore = defineStore('auth', () => {
  const restored = readSession()
  const user = ref<AuthenticatedUser | null>(restored?.user ?? null)
  const accessToken = ref<string | null>(restored?.accessToken ?? null)
  const refreshToken = ref<string | undefined>(restored?.refreshToken)
  const expiresAt = ref<string | null>(restored?.expiresAt ?? null)
  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value && (!expiresAt.value || Date.parse(expiresAt.value) > Date.now())))

  function applySession(session: AuthSession): void {
    user.value = session.user
    accessToken.value = session.accessToken
    refreshToken.value = session.refreshToken
    expiresAt.value = session.expiresAt
    saveSession(session)
    chatWebSocket.connect(session.accessToken)
  }

  async function signIn(credentials: { username: string; password: string }): Promise<void> {
    applySession(await authApi.login(credentials))
  }

  async function signOut(notifyServer = true): Promise<void> {
    try {
      if (notifyServer && accessToken.value) await authApi.logout(refreshToken.value)
    } finally { clearLocalSession() }
  }

  function clearLocalSession(): void {
    chatWebSocket.disconnect()
    clearSession()
    user.value = null
    accessToken.value = null
    refreshToken.value = undefined
    expiresAt.value = null
  }

  function hasAnyRole(roles: UserRole[] = []): boolean {
    if (roles.length === 0) return true
    return roles.some((role) => user.value?.roles.includes(role) || (role === 'ROOM_ADMIN' && user.value?.roles.includes('SYSTEM_ADMIN')))
  }

  return { user, accessToken, expiresAt, isAuthenticated, applySession, signIn, signOut, clearLocalSession, hasAnyRole }
})
