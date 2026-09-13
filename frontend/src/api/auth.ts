import { apiClient } from './client'

// The one place this key is spelled. A typo elsewhere would not throw --
// localStorage silently returns null, surfacing as a mysterious logout.
const TOKEN_KEY = 'fraud.token'

export interface LoginResponse {
    token: string
    username: string
    role: 'ANALYST' | 'ADMIN'
}

// Mirrors localStorage so the request interceptor, which runs on every call,
// does not hit a synchronous storage API on the hot path.
let cachedToken: string | null = null

// Trap: the initial read must happen at module load, not on first getToken().
// Without it a page refresh starts with an empty cache and logs the user out
// even though the token is still in storage.
cachedToken = localStorage.getItem(TOKEN_KEY)

// The interceptor clears the token outside React, so it needs a way to tell
// the app to re-render. Without this the UI keeps showing the logged-in view
// with a token that no longer exists, and every request 401s silently.
let onTokenCleared: (() => void) | null = null

export function setOnTokenCleared(callback: (() => void) | null): void {
    onTokenCleared = callback
}
export function getToken(): string | null {
    return cachedToken
}

export function clearToken(): void {
    cachedToken = null
    localStorage.removeItem(TOKEN_KEY)
    onTokenCleared?.()
}

export async function login(username: string, password: string): Promise<LoginResponse> {
    // Deliberately not using apiClient's auth header -- /auth/login is permitAll
    // and an expired token in the header must not affect a fresh login attempt.
    const { data } = await apiClient.post<LoginResponse>('/v1/auth/login', {
        username,
        password,
    })

    cachedToken = data.token
    localStorage.setItem(TOKEN_KEY, data.token)
    return data
}


