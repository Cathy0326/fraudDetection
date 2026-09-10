import { apiClient } from './client'
import type { AlertResponse } from '../types/alert'
import type { AlertStatus } from '../types/enums'

// Mirrors backend GET /api/v1/alerts?status=OPEN (Page<AlertResponse>).
export async function getAlerts(
    status: AlertStatus
): Promise<{ content: AlertResponse[]; totalElements: number }> {
    const response = await apiClient.get('/v1/alerts', { params: { status } })
    return response.data
}

// Mirrors backend PATCH /api/v1/alerts/{id}, body {"status": "CONFIRMED"|"FALSE_POSITIVE"}.
// Return type is void, not AlertResponse -- see design doc's "pessimistic
// update" decision: the caller refetches the list after this resolves rather
// than trusting a response body to patch local state.
export async function reviewAlert(
    id: number,
    outcome: Extract<AlertStatus, 'CONFIRMED' | 'FALSE_POSITIVE'>
): Promise<void> {
    await apiClient.patch(`/v1/alerts/${id}`, { status: outcome })
}