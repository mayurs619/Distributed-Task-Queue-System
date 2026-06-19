// Base URL for the dashboard backend jobs API.
const API_BASE_URL = 'https://distributed-task-queue-system-k17w.onrender.com/api/jobs';

export interface Job {
    id: string;
    payload: string;
    status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
    attemptCount: number;
    maxAttempts: number;
    createdAt: string;
    updatedAt: string;
}

/**
 * Submit a new job payload to the backend jobs endpoint.
 */
export async function submitJob(payload: string): Promise<void> {
    const response = await fetch(API_BASE_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ payload }),
    });

    if (!response.ok) {
        throw new Error('Failed to submit job');
    }
}

/**
 * Retrieve the current job list from the backend.
 * Returns an empty array if the request fails.
 */
export async function fetchJobs(): Promise<Job[]> {
    try {
        const response = await fetch(API_BASE_URL);
        if (!response.ok) return [];
        return await response.json();
    } catch {
        return [];
    }
}

/**
 * Fetch the current pending queue length from metrics.
 */
export async function fetchQueueDepth(): Promise<number> {
    try {
        const response = await fetch(`${API_BASE_URL}/metrics/queue-depth`);
        const data = await response.json();
        return data.queueDepth || 0;
    } catch {
        return 0;
    }
}

/**
 * Fetch the number of currently processing jobs.
 */
export async function fetchProcessingDepth(): Promise<number> {
    try {
        const response = await fetch(`${API_BASE_URL}/metrics/processing-depth`);
        const data = await response.json();
        return data.processingDepth || 0;
    } catch {
        return 0;
    }
}

/**
 * Fetch the dead-letter queue size from metrics.
 */
export async function fetchDlqDepth(): Promise<number> {
    try {
        const response = await fetch(`${API_BASE_URL}/metrics/dlq-depth`);
        const data = await response.json();
        return data.dlqDepth || 0;
    } catch {
        return 0;
    }
}