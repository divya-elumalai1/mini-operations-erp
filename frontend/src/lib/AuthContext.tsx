import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { Role } from '../types/api'
import { clearStoredToken, getStoredToken, storeToken } from '../api/client'

export interface AuthUser {
  username: string
  fullName: string
  role: Role
}

interface AuthContextValue {
  user: AuthUser | null
  loginSuccess: (response: { token: string; username: string; fullName: string; role: Role }) => void
  logout: () => void
  hasAnyRole: (...roles: Role[]) => boolean
}

const USER_STORAGE_KEY = 'minierp.auth.user'

function loadStoredUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY)
    const token = getStoredToken()
    if (!raw || !token) return null
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Frontend auth state is a UX convenience only. The backend validates the JWT and
 * enforces roles on every request — this context never grants anything.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadStoredUser)

  const loginSuccess = useCallback(
    (response: { token: string; username: string; fullName: string; role: Role }) => {
      storeToken(response.token)
      localStorage.setItem(
        USER_STORAGE_KEY,
        JSON.stringify({ username: response.username, fullName: response.fullName, role: response.role }),
      )
      setUser({ username: response.username, fullName: response.fullName, role: response.role })
    },
    [],
  )

  const logout = useCallback(() => {
    clearStoredToken()
    localStorage.removeItem(USER_STORAGE_KEY)
    setUser(null)
  }, [])

  const hasAnyRole = useCallback(
    (...roles: Role[]) => (user ? roles.includes(user.role) : false),
    [user],
  )

  const value = useMemo(
    () => ({ user, loginSuccess, logout, hasAnyRole }),
    [user, loginSuccess, logout, hasAnyRole],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
