import { request } from '@/api/http'
import type { AuthSession, AuthenticatedUser } from '@/types/api'

export function login(payload: { username: string; password: string }): Promise<AuthSession> {
  return request<AuthSession>({ method: 'post', url: '/auth/login', data: payload, skipAuthRedirect: true })
}

export function register(payload: { username: string; email: string; password: string }): Promise<AuthSession> {
  return request<AuthSession>({ method: 'post', url: '/auth/register', data: payload, skipAuthRedirect: true })
}

export function currentUser(): Promise<AuthenticatedUser> {
  return request<AuthenticatedUser>({ method: 'get', url: '/users/me' })
}

export function logout(refreshToken?: string): Promise<{ loggedOut: boolean }> {
  return request({ method: 'post', url: '/auth/logout', data: { refreshToken }, skipErrorMessage: true, skipAuthRedirect: true })
}
