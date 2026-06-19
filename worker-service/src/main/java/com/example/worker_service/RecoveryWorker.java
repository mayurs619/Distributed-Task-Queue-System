package com.example.worker_service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Periodically scans the processing queue and recovers stuck jobs.
 */
public class RecoveryWorker {

    private final StringRedisTemplate redisTemplate;
    private final JobRepository jobRepository;

    private static final String QUEUE_NAME = "task_queue";
    private static final String PROCESSING_QUEUE = "task_processing";
    private static final long STUCK_TIMEOUT_SECONDS = 60; // Configurable threshold

    @Scheduled(fixedDelay = 30000)
    public void recoverStuckJobs() {

        Long processingSize = redisTemplate.opsForList().size(PROCESSING_QUEUE);

        if (processingSize == null || processingSize == 0) {
            return;
        }

        log.info("Recovery sweep initiated. Checking {} jobs in processing queue...", processingSize);

        // Iterate exactly 'processingSize' times to avoid an infinite loop
        for (int i = 0; i < processingSize; i++) {
            
            String jobId = redisTemplate.opsForList().rightPop(PROCESSING_QUEUE);
            if (jobId == null) break;

            Optional<Job> optionalJob = jobRepository.findById(jobId);

            if (optionalJob.isPresent()) {
                Job job = optionalJob.get();

                boolean isStuck = job.getStatus() == Job.JobStatus.PROCESSING &&
                        job.getUpdatedAt() != null &&
                        Duration.between(job.getUpdatedAt(), Instant.now()).getSeconds() > STUCK_TIMEOUT_SECONDS;

                if (isStuck) {
                    log.warn("Worker crash detected! Recovering stuck job {} back to main queue.", jobId);
                    job.setStatus(Job.JobStatus.PENDING);
                    jobRepository.save(job);
                    redisTemplate.opsForList().leftPush(QUEUE_NAME, jobId);
                } else {
                    // Job is healthy, quietly put it back in the processing queue
                    redisTemplate.opsForList().leftPush(PROCESSING_QUEUE, jobId);
                }
            } else {
                log.error("Found ghost job {} in processing queue with no DB record. Discarding.", jobId);
            }
        }
    }
}