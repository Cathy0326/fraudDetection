import { useEffect, useState } from 'react'
import { TransactionPage } from './pages/TransactionPage'
import { AlertPage } from './pages/AlertPage'
import { LoginPage } from './pages/LoginPage'
import { clearToken, getToken, setOnTokenCleared } from './api/auth'
import './App.css'

// Decision: a plain useState tab switch, not react-router. Neither page needs
// a shareable URL, browser back/forward history, or nested routes -- a
// routing library here would be configuration for a need that doesn't exist.
type Tab = 'transactions' | 'alerts'

const TABS: { id: Tab; label: string }[] = [
    { id: 'transactions', label: 'Transactions' },
    { id: 'alerts', label: 'Alerts' },
]

function App() {
    const [activeTab, setActiveTab] = useState<Tab>('transactions')
    // Lazy initialiser: reads storage once on mount, not on every render.
    const [authed, setAuthed] = useState(() => getToken() !== null)

    // The 401 interceptor runs outside React and clears the token. This is the
    // bridge that turns that into a re-render -- without it the app keeps
    // showing the logged-in view against a token that no longer exists.
    useEffect(() => {
        setOnTokenCleared(() => setAuthed(false))
        return () => setOnTokenCleared(null)
    }, [])

    if (!authed) {
        return <LoginPage onSuccess={() => setAuthed(true)} />
    }

    return (
        <div className="app">
            {/* Trap fixed here: the selected tab used to be `disabled`, which stops the
          browser from dispatching click events at all. Selection is a state, not
          an unavailable action -- express it with aria-selected + a class so the
          button stays clickable and screen readers announce it correctly. */}
            <nav className="tabs" role="tablist" aria-label="Views">
                {TABS.map(({ id, label }) => (
                    <button
                        key={id}
                        role="tab"
                        type="button"
                        aria-selected={activeTab === id}
                        className={activeTab === id ? 'tab tab--active' : 'tab'}
                        onClick={() => setActiveTab(id)}
                    >
                        {label}
                    </button>
                ))}
                <button type="button" className="tab" onClick={() => clearToken()}>
                    Sign out
                </button>
            </nav>

            <main role="tabpanel">
                {activeTab === 'transactions' ? <TransactionPage /> : <AlertPage />}
            </main>
        </div>
    )
}

export default App
