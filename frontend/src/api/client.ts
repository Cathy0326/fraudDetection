import axios from 'axios'

// '/api' works unchanged in both environments: dev goes through vite's
// server.proxy, prod is same-origin from the jar. No environment branching.
export const apiClient = axios.create({
    baseURL: '/api',
    timeout: 10_000,
})

export type ApiErrorKind = 'network' | 'timeout' | 'client' | 'server'

export interface ApiError {
    kind: ApiErrorKind
    detail: string | null
}

// Classify here, phrase at the call site: the network/server distinction is the
// same on every page, the wording is not.
export function classifyError(error: unknown): ApiError {
    if (!axios.isAxiosError(error)) {
        return { kind: 'server', detail: null }
    }

    // Timeout also has no response, but means the opposite advice to the user:
    // the server may be up and slow, so don't tell them to go start it.
    if (error.code === 'ECONNABORTED') {
        return { kind: 'timeout', detail: null }
    }

    // error.response is undefined for network failures, set for 4xx/5xx.
    if (!error.response) {
        return { kind: 'network', detail: null }
    }

    const { status, data } = error.response
    // Guard the shape: a 502 comes from the vite proxy as HTML, not our
    // error contract.
    const detail =
        data && typeof data === 'object' && typeof (data as { message?: unknown }).message === 'string'
            ? (data as { message: string }).message
            : null

    return { kind: status >= 500 ? 'server' : 'client', detail }
}

// Phrase the four error kinds once; callers only supply their own fallback.
export function errorMessage(error: unknown, fallback: string): string {
    const { kind, detail } = classifyError(error)
    if (kind === 'network') return 'Cannot reach the server. Check that the backend is running.'
    if (kind === 'timeout') return 'The server took too long to respond. Please try again.'
    return detail ?? fallback
}

// Log and re-throw, never swallow -- call sites decide how to present failure.
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        const { kind, detail } = classifyError(error)
        console.error(`API ${kind} error:`, detail ?? (error as Error).message)
        return Promise.reject(error)
    }
)
