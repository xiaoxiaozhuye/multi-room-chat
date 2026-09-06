import type { ApiErrorBody } from '@/types/api'

const MESSAGES: Record<string, string> = {
  UNAUTHENTICATED: '登录已失效，请重新登录。',
  TOKEN_EXPIRED: '登录已过期，请重新登录。',
  FORBIDDEN: '你没有执行此操作的权限。',
  ROOM_ACCESS_DENIED: '你没有访问该聊天室的权限。',
  ROOM_DELETED: '该聊天室已删除。',
  ROOM_PAUSED: '该聊天室已暂停。',
  ROOM_CLOSED: '该聊天室已关闭。',
  TOO_MANY_REQUESTS: '操作过于频繁，请稍后再试。',
  SERVICE_UNAVAILABLE: '服务暂不可用，请稍后重试。',
}

export function errorMessage(error: ApiErrorBody | undefined, fallback = '请求失败，请稍后重试。'): string {
  if (!error) return fallback
  return MESSAGES[error.code] ?? error.message ?? fallback
}
