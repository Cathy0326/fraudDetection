import { useState } from 'react'
import { login } from '../api/auth'
import {classifyError, errorMessage} from '../api/client'

interface LoginPageProps {
    onSuccess: () => void
}

export function LoginPage({ onSuccess }: LoginPageProps) {
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    async function handleSubmit(e: React.FormEvent) {
        // Without this the browser performs a real form post and reloads the
        // page, discarding all React state including the token we just stored.
        e.preventDefault()

        setSubmitting(true)
        setError(null)
        try {
            await login(username, password)
            onSuccess()
        } catch (err) {
            // Only a 401 means bad credentials. Anything else -- 502 from the
            // proxy, a 500, a timeout -- must not be reported as a wrong
            // password, or a stopped backend looks like a typo to the user.
            const { kind } = classifyError(err)
            setError(
                kind === 'client'
                    ? 'Invalid username or password.'
                    : errorMessage(err, 'Sign-in failed. Please try again.')
            )
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="login">
            <h1>Sign in</h1>

            {/* onSubmit, not a button onClick: pressing Enter in a field fires
                submit but never a button's click handler. */}
            <form onSubmit={handleSubmit}>
                <label htmlFor="username">Username</label>
                <input
                    id="username"
                    type="text"
                    autoComplete="username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    required
                />

                <label htmlFor="password">Password</label>
                <input
                    id="password"
                    type="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                />

                {error && <p className="error" role="alert">{error}</p>}

                {/* Only the button is disabled while submitting -- the fields stay
                    editable so the user can correct a typo without waiting. */}
                <button type="submit" disabled={submitting}>
                    {submitting ? 'Signing in...' : 'Sign in'}
                </button>
            </form>
        </div>
    )
}
