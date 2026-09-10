import { apiClient } from './client'
import type { TransactionResponse } from '../types/transaction'

// Mirrors backend GET /api/v1/transactions (Page<TransactionResponse>).
// params is untyped `Record<string, unknown>` for now, not a strict interface --
// TransactionSearchCriteria has 8 fields and we're building the filter UI
// incrementally. A strict type here today would need editing every time we
// wire up one more filter field; that's premature for a page not yet built.
export async function getTransactions(
    params: Record<string, unknown> = {}
): Promise<{ content: TransactionResponse[]; totalElements: number }> {
    const response = await apiClient.get('/v1/transactions', { params })
    return response.data
}