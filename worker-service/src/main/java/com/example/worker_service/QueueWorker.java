package com.example.worker_service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Background worker that polls Redis and processes queued jobs.
 */
public class QueueWorker {

    private final StringRedisTemplate redisTemplate;
    private final JobRepository jobRepository;
    private final QueueMetrics metrics;
    private static final String PROCESSING_QUEUE = "task_processing";
    private static final String QUEUE_NAME = "task_queue";
    private static final String DLQ_NAME = "task_queue_dlq";

    @Scheduled(fixedDelay = 1000)
    public void pollQueue() {

        // Atomically move a job from the main queue to the processing queue.
        String jobId = redisTemplate.opsForList().rightPopAndLeftPush(QUEUE_NAME, PROCESSING_QUEUE);

        if (jobId != null) {
            log.info("Picked up job {}", jobId);
            processJob(jobId);
        }
    }

    private void processJob(String jobId) {

        // Load the job record from the database before processing.
        Optional<Job> optionalJob = jobRepository.findById(jobId);

        if (optionalJob.isEmpty()) {
            log.warn("Job {} found in Redis but not in PostgreSQL", jobId);
            // Clean up the ghost job from the processing queue
            redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, jobId);
            return;
        }

        Job job = optionalJob.get();

        try {
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setStatus(Job.JobStatus.PROCESSING);
            jobRepository.save(job);

            log.info("Processing job {} (Attempt {}/{})", jobId, job.getAttemptCount(), job.getMaxAttempts());

            // Simulate heavy computation
            Thread.sleep(2000);

            // Deterministic failure for testing
            if (job.getPayload() != null && job.getPayload().contains("simulate-failure")) {
                throw new RuntimeException("Simulated external API timeout");
            }

            job.setStatus(Job.JobStatus.COMPLETED);
            jobRepository.save(job);

            redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, jobId);
            metrics.getJobsProcessed().increment();

            log.info("Job {} completed successfully", jobId);

        } catch (Exception e) {

            log.error("Job {} failed: {}", jobId, e.getMessage());

            if (job.getAttemptCount() < job.getMaxAttempts()) {

                long backoffSeconds = (long) Math.pow(2, job.getAttemptCount());

                try {
                    Thread.sleep(backoffSeconds * 1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }

                job.setStatus(Job.JobStatus.PENDING);
                jobRepository.save(job);
                metrics.getJobsRetried().increment();

                // FIX: Push back to main queue before removing from processing queue
                redisTemplate.opsForList().leftPush(QUEUE_NAME, jobId);
                redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, jobId);

                log.warn("Re-queued job {} after {} seconds", jobId, backoffSeconds);

            } else {

                job.setStatus(Job.JobStatus.FAILED);
                jobRepository.save(job);
                metrics.getJobsFailed().increment();

                // FIX: Push to Dead Letter Queue before removing from processing queue
                redisTemplate.opsForList().leftPush(DLQ_NAME, jobId);
                redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, jobId);

                log.error("Job {} exceeded max attempts. Moved to DLQ", jobId);
            }
        }
    }
}