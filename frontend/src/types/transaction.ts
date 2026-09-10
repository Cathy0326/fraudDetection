import type { Decision } from './enums'

// Mirrors backend enum com.cathy.frauddetection.transaction.TransactionStatus.
// Pipeline state (has the consumer processed this event?), not a business
// outcome -- that distinction lives in `Decision`, a separate field below.
export type TransactionStatus = 'PENDING' | 'PROCESSED' | 'FAILED'

// Mirrors backend record com.cathy.frauddetection.transaction.TransactionResponse.
export interface TransactionResponse {
    id: number
    transactionRef: string
    accountId: string
    amount: string          // BigDecimal serializes to a JSON string, not a JS
    // number -- see note below, this is not a typo.
    currency: string
    destinationCountry: string
    transactionType: string
    occurredAt: string       // ISO 8601 instant string
    status: TransactionStatus
    riskScore: number | null // null until the consumer has scored this transaction
    decision: Decision | null // same null-until-scored rule as riskScore above;
    // the two fields are set together, never one without
    // the other (see the entity's applyRiskAssessment)
    createdAt: string
}