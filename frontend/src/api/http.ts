import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiEnvelope, ApiErrorBody, ApiErrorEnvelope } from '@/types/api'
import { errorMessage } from '@/utils/error-message'
import { getAccessToken } from '@/utils/token-storage'
import { createRequestId } from '@/utils/request-id'

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipErrorMessage?: boolean
    skipAuthRedirect?: boolean
  }
}

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly apiError: ApiErrorBody, public readonly requestId?: string) {
    super(errorMessage(apiError))
  }
}

let onUnauthorized: (() => void) | undefined
let handlingUnauthorized = false

export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  if (config.method && !['get', 'head', 'options'].includes(config.method.toLowerCase())) {
    config.headers['X-Request-Id'] ??= createRequestId()
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiEnvelope<unknown> | ApiErrorEnvelope
    if ('error' in body && body.error) {
      const error = new ApiError(response.status, body.error, body.requestId)
      if (!response.config.skipErrorMessage) ElMessage.error(error.message)
      return Promise.reject(error)
    }
    return response
  },
  (reason: AxiosError<ApiErrorEnvelope>) => {
    const config = reason.config
    const status = reason.response?.status ?? 0
    const body = reason.response?.data
    const apiError = body?.error ?? { code: status === 0 ? 'NETWORK_ERROR' : 'HTTP_ERROR', message: status === 0 ? '网络连接失败，请检查网络后重试。' : '请求失败，请稍后重试。' }
    const error = new ApiError(status, apiError, body?.requestId)
    if (!config?.skipErrorMessage) ElMessage.error(error.message)
    if (status === 401 && !config?.skipAuthRedirect && !handlingUnauthorized) {
      handlingUnauthorized = true
      onUnauthorized?.()
      queueMicrotask(() => { handlingUnauthorized = false })
    }
    return Promise.reject(error)
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiEnvelope<T>>(config)
  return response.data.data
}

export default http
