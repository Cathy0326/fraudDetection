import { useEffect, useState } from 'react'
import { getTransactions } from '../api/transactions'
import type { TransactionResponse } from '../types/transaction'
import { errorMessage } from '../api/client'

const PAGE_SIZE = 20

export function TransactionPage() {
    const [transactions, setTransactions] = useState<TransactionResponse[]>([])
    const [totalElements, setTotalElements] = useState(0)
    const [page, setPage] = useState(0)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        // Trap: without this flag, a slow earlier request can resolve AFTER a
        // faster later one (e.g. user double-clicks "next page") and silently
        // overwrite newer state with stale data. `ignore` marks this effect run
        // as stale once a newer one has started, so its response is discarded
        // instead of applied.
        let ignore = false
        setLoading(true)
        setError(null)

        getTransactions({ page, size: PAGE_SIZE })
            .then((data) => {
                if (ignore) return
                setTransactions(data.content)
                setTotalElements(data.totalElements)
            })
            .catch((err) => {
                if (ignore) return
                setError(errorMessage(err, 'Failed to load transactions.'))
                setTransactions([])
                setTotalElements(0)
            })           .finally(() => {
            if (ignore) return
            setLoading(false)
        })

        return () => {
            ignore = true
        }
    }, [page])

    const totalPages = Math.ceil(totalElements / PAGE_SIZE)

    // Decision: these four states are mutually exclusive and ordered by how much
    // we actually know. Rendering them as independent `&&` blocks let an error
    // message and an empty table appear at the same time -- two contradictory
    // claims about the same request.
    function renderBody() {
        if (loading) return <p className="empty">Loading...</p>
        if (error) return <p className="error" role="alert">{error}</p>
        if (transactions.length === 0) return <p className="empty">No transactions.</p>

        return (
            <table>
                <thead>
                <tr>
                    <th>Ref</th>
                    <th>Account</th>
                    <th className="num">Amount</th>
                    <th>Country</th>
                    <th>Status</th>
                    <th className="num">Risk score</th>
                    <th>Decision</th>
                </tr>
                </thead>
                <tbody>
                {transactions.map((tx) => (
                    <tr key={tx.id}>
                        <td>{tx.transactionRef}</td>
                        <td>{tx.accountId}</td>
                        {/* amount stays a string here -- it came from the backend as one
                            (BigDecimal serializes to JSON string, not a number), and
                            display doesn't need arithmetic, so there's no reason to
                            parse it and risk reintroducing float precision issues. */}
                        <td className="num">{tx.amount} {tx.currency}</td>
                        <td>{tx.destinationCountry}</td>
                        <td>{tx.status}</td>
                        <td className="num">{tx.riskScore ?? '—'}</td>
                        <td>{tx.decision ?? '—'}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        )
    }

    return (
        <div>
            <h1>Transactions</h1>

            {renderBody()}

            <div className="pagination">
                <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                    Previous
                </button>
                <span>Page {page + 1} of {totalPages || 1}</span>
                <button
                    disabled={page + 1 >= totalPages}
                    onClick={() => setPage((p) => p + 1)}
                >
                    Next
                </button>
            </div>
        </div>
    )
}
