import axios from 'axios'

const PROD_BACKEND_URL = 'https://earntrap-backend.onrender.com'

/**
 * Normalizes the API base URL to ensure proper endpoint resolution:
 * - If VITE_API_BASE_URL is provided, it is sanitized (trailing slashes stripped)
 *   and guaranteed to end with '/api' without duplication (e.g. '.../api/api').
 * - If VITE_API_BASE_URL is omitted:
 *   - On localhost/127.0.0.1 (local development), falls back to '/api' (Vite dev proxy).
 *   - On production domains (e.g. earn-trap.vercel.app), defaults to
 *     'https://earntrap-backend.onrender.com/api'.
 */
const rawEnvUrl = typeof import.meta !== 'undefined' && import.meta?.env
  ? import.meta.env.VITE_API_BASE_URL
  : undefined

export function resolveApiBaseUrl(raw = rawEnvUrl) {
  const trimmed = (raw || '').trim().replace(/\/+$/, '')

  if (trimmed && trimmed !== '/' && trimmed !== '/api') {
    if (trimmed.endsWith('/api')) {
      return trimmed
    }
    return `${trimmed}/api`
  }

  // Fallback when environment variable is not explicitly set
  if (typeof window !== 'undefined' && window.location) {
    const host = window.location.hostname
    if (host && host !== 'localhost' && host !== '127.0.0.1') {
      return `${PROD_BACKEND_URL}/api`
    }
  }

  return '/api'
}

const api = axios.create({
  baseURL: resolveApiBaseUrl(),
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach the saved JWT to protected API requests and guard against duplicated '/api' prefix.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('earntrap_token')

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  // Guard: if caller accidentally passes '/api/auth/register' while baseURL already has '/api',
  // strip redundant leading '/api' to prevent '/api/api/auth/register'.
  if (config.url && config.url.startsWith('/api/')) {
    config.url = config.url.replace(/^\/api/, '')
  } else if (config.url && config.url === '/api') {
    config.url = '/'
  }

  return config
})

export default api
