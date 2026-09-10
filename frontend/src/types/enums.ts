// Mirrors backend enum com.cathy.frauddetection.transaction.Decision.
// A TS union of string literals, not a TS `enum` -- the wire value IS the
// plain string Jackson serializes the Java enum constant into, so a union
// maps 1:1 to what actually arrives over the network. A TS enum would add
// a layer of indirection (numeric backing values, reverse mapping) that
// buys nothing here.
export type Decision = 'APPROVE' | 'REVIEW' | 'BLOCK'

// Mirrors backend enum com.cathy.frauddetection.alert.AlertStatus.
export type AlertStatus = 'OPEN' | 'CONFIRMED' | 'FALSE_POSITIVE'