package com.example.producerapi;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobRepository;
    private final StringRedisTemplate redisTemplate;

    // Redis keys used for enqueueing and idempotency tracking.
    private static final String QUEUE_NAME = "task_queue";
    private static final String DLQ_NAME = "task_queue_dlq";
    private static final String IDEMP_PREFIX = "idemp:";

    // Persist a new job and enqueue it for processing.
    @PostMapping
    public ResponseEntity<Map<String, String>> submitJob(
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,

            @RequestBody Map<String, String> request) {

        if (idempotencyKey != null) {
            String existingJobId = redisTemplate.opsForValue()
                    .get(IDEMP_PREFIX + idempotencyKey);

            if (existingJobId != null) {
                return ResponseEntity.ok(
                        Map.of(
                                "message", "Job already accepted (Idempotent return)",
                                "jobId", existingJobId
                        )
                );
            }
        }

        Job job = new Job();
        job.setPayload(request.getOrDefault("payload", "empty_task"));

        jobRepository.save(job);

        // Push the new job ID onto the Redis queue.
        redisTemplate.opsForList().leftPush(QUEUE_NAME, job.getId());

        if (idempotencyKey != null) {
            redisTemplate.opsForValue().set(
                    IDEMP_PREFIX + idempotencyKey,
                    job.getId(),
                    Duration.ofHours(24)
            );
        }

        return ResponseEntity.accepted().body(
                Map.of(
                        "message", "Job accepted",
                        "jobId", job.getId()
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(@PathVariable String id) {
        return jobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Dashboard-friendly endpoint that returns all jobs.
    @GetMapping
    public ResponseEntity<List<Job>> getAllJobs() {
        return ResponseEntity.ok(jobRepository.findAll());
    }

    // Return only jobs that have failed processing.
    @GetMapping("/failed")
    public List<Job> getFailedJobs() {
        return jobRepository.findAll()
                .stream()
                .filter(job -> job.getStatus() == Job.JobStatus.FAILED)
                .toList();
    }

    // Return the current length of the pending Redis queue.
    @GetMapping("/metrics/queue-depth")
    public Map<String, Long> queueDepth() {
        Long depth = redisTemplate.opsForList().size(QUEUE_NAME);
        return Map.of("queueDepth", depth == null ? 0 : depth);
    }

    // Return the current length of the dead letter queue.
    @GetMapping("/metrics/dlq-depth")
    public Map<String, Long> dlqDepth() {
        Long depth = redisTemplate.opsForList().size(DLQ_NAME);
        return Map.of("dlqDepth", depth == null ? 0 : depth);
    }

    // Return the count of currently processing jobs.
    @GetMapping("/metrics/processing-depth")
    public Map<String, Long> processingDepth() {
        Long depth = redisTemplate.opsForList().size("task_processing");
        return Map.of("processingDepth", depth == null ? 0 : depth);
    }
}