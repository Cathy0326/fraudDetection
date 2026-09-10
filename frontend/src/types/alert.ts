import type { Decision, AlertStatus } from './enums'

// Mirrors backend record com.cathy.frauddetection.alert.AlertResponse.
// Field names match the Java record exactly -- Jackson serializes record
// components in declaration order using their exact camelCase names, so
// this is a direct 1:1 mapping, not a reinterpretation of the contract.
export interface AlertResponse {
    id: number
    transactionId: number
    riskScore: number
    decision: Decision
    triggeredRules: string[]
    status: AlertStatus
    createdAt: string        // ISO 8601 instant string; use `new Date(...)` to parse
    reviewedAt: string | null // `| null`, not optional `?` -- the backend always
    // serializes this key, just with a null value when
    // unreviewed (see AlertResponse.java's own doc comment).
    // Marking it optional (`reviewedAt?: string`) would
    // wrongly claim the key can be absent, which the
    // backend explicitly designed against.
}