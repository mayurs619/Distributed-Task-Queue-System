package com.example.worker_service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for worker service job persistence.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, String> {
}