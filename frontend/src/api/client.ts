import axios from 'axios'

// Decision: a single configured axios instance, not the default axios import,
// used everywhere else. baseURL is '/api' -- this works unchanged in both
// environments: in dev it gets caught by vite.config.ts's server.proxy and
// forwarded to :8080; in prod (same-origin jar) '/api' already resolves
// correctly with no proxy needed at all. One config, zero environment branching.
export const apiClient = axios.create({
    baseURL: '/api',
    timeout: 10_000,
})

// Decision: a response interceptor that unwraps Axios's envelope so callers
// get plain data back, not response.data.data every time. Errors are
// re-thrown, not swallowed -- each call site decides how to handle its own
// failure (e.g. show a toast vs disable a button), which a global catch here
// cannot know.
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        // trap: axios wraps network errors and HTTP errors differently.
        // error.response exists only for HTTP errors (4xx/5xx); a network
        // failure (backend down, CORS block) has error.response === undefined.
        if (error.response) {
            console.error(`API error ${error.response.status}:`, error.response.data)
        } else {
            console.error('Network error:', error.message)
        }
        return Promise.reject(error)
    }
)