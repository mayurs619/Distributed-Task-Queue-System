package com.example.worker_service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Getter
/**
 * Registers application metrics exposed to Micrometer.
 */
public class QueueMetrics {

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;

    private Counter jobsProcessed;
    private Counter jobsFailed;
    private Counter jobsRetried;

    private static final String QUEUE_NAME = "task_queue";
    private static final String DLQ_NAME = "task_queue_dlq";
    private static final String PROCESSING_QUEUE = "task_processing";

    @PostConstruct
    public void init() {

        jobsProcessed =
                meterRegistry.counter("jobs.processed");

        jobsFailed =
                meterRegistry.counter("jobs.failed");

        jobsRetried =
                meterRegistry.counter("jobs.retried");

        // Register queue depth gauges backed by Redis list sizes.

        Gauge.builder(
                        "queue.depth",
                        () -> {
                            Long size =
                                    redisTemplate.opsForList()
                                            .size(QUEUE_NAME);

                            return size == null ? 0 : size;
                        })
                .register(meterRegistry);

        Gauge.builder(
                        "dlq.depth",
                        () -> {
                            Long size =
                                    redisTemplate.opsForList()
                                            .size(DLQ_NAME);

                            return size == null ? 0 : size;
                        })
                .register(meterRegistry);
        Gauge.builder(
                    "processing.depth",
                    () -> {
                        Long size =
                                redisTemplate.opsForList()
                                        .size(PROCESSING_QUEUE);

                        return size == null ? 0 : size;
                    })
                .register(meterRegistry);
    }
}