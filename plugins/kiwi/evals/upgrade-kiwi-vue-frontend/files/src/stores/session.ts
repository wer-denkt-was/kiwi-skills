import { ref } from 'vue'
import { FetchApi, unwrapResponseJson } from '@werdenktwas/kiwi-vue/services'
import { CredentialsInvalid, JwtBearerToken, missingCredentials, type ApiCredentials } from '@werdenktwas/kiwi-vue/api'

export const apiUrl = import.meta.env.VITE_API_URL ?? 'https://todo.example.com/api/'
export const credentials = ref<ApiCredentials>(missingCredentials)

// API client used outside of components (login, health check)
const api = new FetchApi(apiUrl, (input, init) => {
  const current = credentials.value
  if (!current.isValid()) throw new CredentialsInvalid(current)
  return [current.rewrite?.(input) ?? input, current.authenticate(init)]
})

export async function login(token: string) {
  credentials.value = new JwtBearerToken(token)
}

/** Spring Boot actuator health endpoint, served at the root of the backend origin (not below /api/) */
export async function backendHealth(): Promise<string> {
  const response = await api.request('/actuator/health', {}, { method: 'GET' })
  const body = await unwrapResponseJson<{ status: string }>(response)
  return body.status
}

export function describeLoginError(error: unknown): string {
  if (error instanceof CredentialsInvalid) {
    return `Session expired for ${String(error.details.credentials)}`
  }
  return 'Login failed'
}
