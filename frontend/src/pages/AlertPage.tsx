import { useEffect, useRef, useState } from 'react'
import { getAlerts, reviewAlert } from '../api/alerts'
import type { AlertResponse } from '../types/alert'
import { errorMessage } from '../api/client'

export function AlertPage() {
    const [alerts, setAlerts] = useState<AlertResponse[]>([])
    const [loading, setLoading] = useState(true)
    // Decision: two separate errors, not one. A failed *load* means we have no
    // rows to show, so it replaces the table. A failed *review* means the rows
    // are still valid -- blanking the table would hide the very row the user
    // needs to retry. Same variable for both would force one to behave wrongly.
    const [loadError, setLoadError] = useState<string | null>(null)
    const [reviewError, setReviewError] = useState<string | null>(null)
    // Tracks which single row has a review in flight, not a page-wide boolean --
    // disabling only that row's buttons keeps the rest of the table usable
    // while one submission is pending, instead of freezing the whole page.
    const [reviewingId, setReviewingId] = useState<number | null>(null)

    // One "is this component still mounted" flag shared by every fetch, so a
    // response arriving after unmount is discarded instead of calling setState
    // on a dead component.
    const cancelledRef = useRef(false)

    // `showLoading` false = background refresh after a review. Flipping the
    // page-wide loading flag there would swap the whole table for "Loading...",
    // undoing the per-row disabling above.
    function loadAlerts(showLoading: boolean) {
        if (showLoading) setLoading(true)
        setLoadError(null)

        getAlerts('OPEN')
            .then((data) => {
                if (cancelledRef.current) return
                setAlerts(data.content)
            })
            .catch((err) => {
                if (cancelledRef.current) return
                setLoadError(errorMessage(err, 'Failed to load alerts.'))
                setAlerts([])
            })           .finally(() => {
            if (cancelledRef.current) return
            if (showLoading) setLoading(false)
        })
    }

    useEffect(() => {
        // Trap: React 18 StrictMode mounts, unmounts, then remounts in dev. The
        // cleanup below sets this to true, so it MUST be reset to false here --
        // otherwise the second mount's request is discarded and the page stays
        // permanently empty, with no error to explain it.
        cancelledRef.current = false
        loadAlerts(true)
        return () => {
            cancelledRef.current = true
        }
    }, [])

    async function handleReview(
        id: number,
        outcome: 'CONFIRMED' | 'FALSE_POSITIVE'
    ) {
        setReviewingId(id)
        setReviewError(null)
        try {
            await reviewAlert(id, outcome)
            loadAlerts(false)
        } catch (err) {
            if (cancelledRef.current) return
            setReviewError(errorMessage(err, 'Failed to submit review. Please try again.'))
        } finally {
            if (!cancelledRef.current) setReviewingId(null)
        }
    }

    // Mutually exclusive, ordered by how much we actually know about the data.
    function renderBody() {
        if (loading) return <p className="empty">Loading...</p>
        if (loadError) return <p className="error" role="alert">{loadError}</p>
        if (alerts.length === 0) return <p className="empty">No open alerts.</p>

        return (
            <table>
                <thead>
                <tr>
                    <th>Transaction</th>
                    <th className="num">Risk score</th>
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
                            <td className="num">{alert.riskScore}</td>
                            <td>{alert.decision}</td>
                            <td>{alert.triggeredRules.join(', ')}</td>
                            <td>{new Date(alert.createdAt).toLocaleString()}</td>
                            <td className="actions">
                                <button
                                    type="button"
                                    disabled={isSubmitting}
                                    onClick={() => handleReview(alert.id, 'CONFIRMED')}
                                >
                                    Confirm
                                </button>
                                <button
                                    type="button"
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
        )
    }

    return (
        <div>
            <h1>Open alerts</h1>

            {/* Sits outside renderBody deliberately: a review failure must not
                remove the table, or the user loses the row they need to retry. */}
            {reviewError && <p className="error" role="alert">{reviewError}</p>}

            {renderBody()}
        </div>
    )
}
