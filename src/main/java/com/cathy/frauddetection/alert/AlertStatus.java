package com.cathy.frauddetection.alert;

/**
 * Review lifecycle state of an alert.
 *
 * <p>Values are mirrored by the ck_alerts_status CHECK constraint (V4). There is no
 * compile-time link between the two: EnumSyncTest reads the real constraint definition
 * from pg_constraint and compares it against values().
 *
 * <p>Declaration order is a public contract because values() drives the UI ordering.
 * It is safe for persistence only because the entity maps this with EnumType.STRING.
 */
public enum AlertStatus {

    /** Created by the consumer, not yet reviewed. reviewed_at must be null. */
    OPEN,

    /** A human confirmed the alert is genuine fraud. reviewed_at must be set. */
    CONFIRMED,

    /** A human judged the alert to be wrong. reviewed_at must be set. */
    FALSE_POSITIVE
}