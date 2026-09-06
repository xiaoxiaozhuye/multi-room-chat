import type { AuthSession, AuthenticatedUser } from '@/types/api'

const ACCESS_TOKEN_KEY = 'multi-room-chat.access-token'
const REFRESH_TOKEN_KEY = 'multi-room-chat.refresh-token'
const USER_KEY = 'multi-room-chat.user'
const EXPIRES_AT_KEY = 'multi-room-chat.expires-at'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function readSession(): AuthSession | null {
  const accessToken = getAccessToken()
  const userRaw = localStorage.getItem(USER_KEY)
  const expiresAt = localStorage.getItem(EXPIRES_AT_KEY)
  if (!accessToken || !userRaw || !expiresAt) return null

  try {
    return { user: JSON.parse(userRaw) as AuthenticatedUser, accessToken, refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY) ?? undefined, expiresAt }
  } catch {
    clearSession()
    return null
  }
}

export function saveSession(session: AuthSession): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken)
  localStorage.setItem(USER_KEY, JSON.stringify(session.user))
  localStorage.setItem(EXPIRES_AT_KEY, session.expiresAt)
  if (session.refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken)
  else localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(EXPIRES_AT_KEY)
}
