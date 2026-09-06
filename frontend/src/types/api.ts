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
