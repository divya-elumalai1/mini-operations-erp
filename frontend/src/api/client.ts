import axios, { AxiosError } from 'axios'
import type { ApiError } from '../types/api'

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api'

const TOKEN_STORAGE_KEY = 'minierp.auth.token'

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function storeToken(token: string): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, token)
}

export function clearStoredToken(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}

export const api = axios.create({
  baseURL: API_BASE_URL,
})

api.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** Extracts the backend's structured error envelope from any failure. */
export function extractApiError(err: unknown): ApiError {
  if (err instanceof AxiosError && err.response?.data) {
    const data = err.response.data as Partial<ApiError>
    return {
      status: err.response.status,
      error: data.error,
      message: data.message,
      details: Array.isArray(data.details) ? data.details : undefined,
    }
  }
  return { message: 'Network error — is the backend running?' }
}
