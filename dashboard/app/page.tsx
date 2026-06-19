// src/app/page.tsx
'use client';

import { useState, useEffect } from 'react';
import { 
    submitJob, 
    fetchJobs, 
    fetchQueueDepth, 
    fetchProcessingDepth, 
    fetchDlqDepth, 
    Job 
} from '@/lib/api';

export default function Dashboard() {
    const [jobs, setJobs] = useState<Job[]>([]);
    const [metrics, setMetrics] = useState({ queue: 0, processing: 0, dlq: 0 });
    const [payloadInput, setPayloadInput] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);

    // Poll backend periodically so the dashboard stays up to date.
    useEffect(() => {
        const loadData = async () => {
            const [fetchedJobs, qDepth, pDepth, dDepth] = await Promise.all([
                fetchJobs(),
                fetchQueueDepth(),
                fetchProcessingDepth(),
                fetchDlqDepth()
            ]);
            setJobs(fetchedJobs);
            setMetrics({ queue: qDepth, processing: pDepth, dlq: dDepth });
        };

        loadData();
        const interval = setInterval(loadData, 2000);
        return () => clearInterval(interval);
    }, []);

    // Submit a new job payload to the backend and clear the form.
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!payloadInput.trim()) return;
        
        setIsSubmitting(true);
        try {
            await submitJob(payloadInput);
            setPayloadInput('');
        } catch (error) {
            console.error(error);
        } finally {
            setIsSubmitting(false);
        }
    };

    // Map job status values to badge color classes.
    const getStatusBadgeColor = (status: string) => {
        switch (status) {
            case 'COMPLETED': return 'bg-emerald-500/10 text-emerald-400 ring-emerald-500/20';
            case 'PROCESSING': return 'bg-amber-500/10 text-amber-400 ring-amber-500/20';
            case 'FAILED': return 'bg-rose-500/10 text-rose-400 ring-rose-500/20';
            default: return 'bg-slate-500/10 text-slate-400 ring-slate-500/20';
        }
    };

    return (
        <div className="min-h-screen bg-[#0a0a0a] text-slate-200 p-8 font-mono">
            <div className="max-w-6xl mx-auto space-y-8">
                
                {/* Header */}
                <div className="border-b border-white/10 pb-6">
                    <h1 className="text-3xl font-bold tracking-tight text-white">Distributed Task Queue</h1>
                    <p className="text-slate-400 mt-2">System Operations Dashboard</p>
                </div>

                {/* Metrics Row */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="bg-white/5 border border-white/10 rounded-xl p-6">
                        <div className="text-sm font-medium text-slate-400 mb-2">Pending Queue</div>
                        <div className="text-4xl font-bold text-white">{metrics.queue}</div>
                    </div>
                    <div className="bg-white/5 border border-amber-500/20 rounded-xl p-6 shadow-[0_0_15px_rgba(245,158,11,0.1)]">
                        <div className="text-sm font-medium text-amber-400 mb-2">In-Flight (Processing)</div>
                        <div className="text-4xl font-bold text-white">{metrics.processing}</div>
                    </div>
                    <div className="bg-white/5 border border-rose-500/20 rounded-xl p-6 shadow-[0_0_15px_rgba(225,29,72,0.1)]">
                        <div className="text-sm font-medium text-rose-400 mb-2">Dead Letter Queue</div>
                        <div className="text-4xl font-bold text-white">{metrics.dlq}</div>
                    </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    
                    {/* Producer Form (Left Column) */}
                    <div className="lg:col-span-1">
                        <div className="bg-white/5 border border-white/10 rounded-xl p-6 sticky top-8">
                            <h2 className="text-lg font-semibold text-white mb-4">Inject Job</h2>
                            <form onSubmit={handleSubmit} className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-slate-400 mb-1">Payload Data</label>
                                    <input 
                                        type="text" 
                                        value={payloadInput}
                                        onChange={(e) => setPayloadInput(e.target.value)}
                                        className="w-full bg-black/50 border border-white/10 rounded-lg px-4 py-2.5 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
                                        placeholder='e.g., {"action": "generate-report"}'
                                    />
                                </div>
                                <button 
                                    type="submit" 
                                    disabled={isSubmitting || !payloadInput.trim()}
                                    className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-medium py-2.5 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    {isSubmitting ? 'Submitting...' : 'Queue Job'}
                                </button>
                            </form>
                        </div>
                    </div>

                    {/* Jobs Table (Right Column) */}
                    <div className="lg:col-span-2">
                        <div className="bg-white/5 border border-white/10 rounded-xl overflow-hidden">
                            <div className="px-6 py-4 border-b border-white/10">
                                <h2 className="text-lg font-semibold text-white">Recent Jobs</h2>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm text-left">
                                    <thead className="text-xs text-slate-400 uppercase bg-black/20 border-b border-white/10">
                                        <tr>
                                            <th className="px-6 py-3">Job ID</th>
                                            <th className="px-6 py-3">Status</th>
                                            <th className="px-6 py-3 text-center">Attempts</th>
                                            <th className="px-6 py-3">Payload</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {jobs.length === 0 ? (
                                            <tr>
                                                <td colSpan={4} className="px-6 py-8 text-center text-slate-500">
                                                    No jobs found in the database.
                                                </td>
                                            </tr>
                                        ) : (
                                            jobs.map((job) => (
                                                <tr key={job.id} className="border-b border-white/5 hover:bg-white/[0.02] transition-colors">
                                                    <td className="px-6 py-4 font-medium text-slate-300 truncate max-w-[150px]">
                                                        {job.id}
                                                    </td>
                                                    <td className="px-6 py-4">
                                                        <span className={`inline-flex items-center rounded-md px-2 py-1 text-xs font-medium ring-1 ring-inset ${getStatusBadgeColor(job.status)}`}>
                                                            {job.status}
                                                        </span>
                                                    </td>
                                                    <td className="px-6 py-4 text-center">
                                                        <span className="text-slate-400">{job.attemptCount} / {job.maxAttempts}</span>
                                                    </td>
                                                    <td className="px-6 py-4 truncate max-w-[200px] text-slate-400">
                                                        {job.payload}
                                                    </td>
                                                </tr>
                                            ))
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>

                </div>
            </div>
        </div>
    );
}