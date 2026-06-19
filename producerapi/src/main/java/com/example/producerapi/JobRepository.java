package com.example.producerapi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for persisting and querying Job entities.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, String> {
}