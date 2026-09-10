import { useEffect, useState } from 'react'
import { getAlerts, reviewAlert } from '../api/alerts'
import type { AlertResponse } from '../types/alert'

export function AlertPage() {
    const [alerts, setAlerts] = useState<AlertResponse[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    // Tracks which single row has a review in flight, not a page-wide boolean --
    // disabling only that row's buttons keeps the rest of the table usable
    // while one submission is pending, instead of freezing the whole page.
    const [reviewingId, setReviewingId] = useState<number | null>(null)

    // Extracted so both the initial mount effect and the post-review refetch
    // call the exact same fetch logic -- one code path, not two copies that
    // can drift apart.
    function loadAlerts(ignoreRef: { current: boolean }) {
        setLoading(true)
        setError(null)

        getAlerts('OPEN')
            .then((data) => {
                if (ignoreRef.current) return
                setAlerts(data.content)
            })
            .catch(() => {
                if (ignoreRef.current) return
                setError('Failed to load alerts.')
            })
            .finally(() => {
                if (ignoreRef.current) return
                setLoading(false)
            })
    }

    useEffect(() => {
        const ignoreRef = { current: false }
        loadAlerts(ignoreRef)
        return () => {
            ignoreRef.current = true
        }
    }, [])

    async function handleReview(
        id: number,
        outcome: 'CONFIRMED' | 'FALSE_POSITIVE'
    ) {
        setReviewingId(id)
        try {
            await reviewAlert(id, outcome)
            // Decision: pessimistic update. Don't splice the reviewed row out of
            // local state by hand -- refetch from the server, which is the single
            // source of truth for "which alerts are still OPEN". Hand-patching
            // local state here risks drifting from the server if this request
            // raced with another change.
            loadAlerts({ current: false })
        } catch {
            setError('Failed to submit review. Please try again.')
        } finally {
            setReviewingId(null)
        }
    }

    return (
        <div>
            <h1>Open alerts</h1>

            {error && <p role="alert">{error}</p>}

            {loading ? (
                <p>Loading...</p>
            ) : alerts.length === 0 ? (
                <p>No open alerts.</p>
            ) : (
                <table>
                    <thead>
                    <tr>
                        <th>Transaction</th>
                        <th>Risk score</th>
                        <th>Decision</th>
                        <th>Triggered rules</th>
                        <th>Created</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    {alerts.map((alert) => {
                        const isSubmitting = reviewingId === alert.id
                        return (
                            <tr key={alert.id}>
                                <td>{alert.transactionId}</td>
                                <td>{alert.riskScore}</td>
                                <td>{alert.decision}</td>
                                <td>{alert.triggeredRules.join(', ')}</td>
                                <td>{new Date(alert.createdAt).toLocaleString()}</td>
                                <td>
                                    <button
                                        disabled={isSubmitting}
                                        onClick={() => handleReview(alert.id, 'CONFIRMED')}
                                    >
                                        Confirm
                                    </button>
                                    <button
                                        disabled={isSubmitting}
                                        onClick={() => handleReview(alert.id, 'FALSE_POSITIVE')}
                                    >
                                        False positive
                                    </button>
                                </td>
                            </tr>
                        )
                    })}
                    </tbody>
                </table>
            )}
        </div>
    )
}