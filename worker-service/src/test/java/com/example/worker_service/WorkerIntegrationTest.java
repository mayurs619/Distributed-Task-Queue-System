package com.example.worker_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class WorkerIntegrationTest {

    // 1. Spin up a temporary PostgreSQL Database
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    // 2. Spin up a temporary Redis instance
    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private QueueWorker queueWorker;

    @Test
    void testSuccessfulJobProcessing() {
        // Arrange: Create a test job in the database
        Job job = new Job();
        job.setPayload("automated-test-payload");
        job = jobRepository.save(job);

        // Arrange: Push the job ID onto the Redis queue
        redisTemplate.opsForList().leftPush("task_queue", job.getId());

        // Act: Manually trigger the worker to poll the queue once
        queueWorker.pollQueue();

        // Assert: Retrieve the job from the database and verify it completed
        Optional<Job> processedJob = jobRepository.findById(job.getId());
        assertTrue(processedJob.isPresent());
        assertEquals(Job.JobStatus.COMPLETED, processedJob.get().getStatus());
        
        // Assert: Verify the processing queue was cleaned up
        Long processingSize = redisTemplate.opsForList().size("task_processing");
        assertEquals(0L, processingSize);
    }
}