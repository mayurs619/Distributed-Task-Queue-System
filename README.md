# Distributed Task Queue System

## Executive Summary
A production-grade, asynchronous task processing architecture designed for high availability and fault tolerance. This system decouples heavy request ingestion from background processing, ensuring reliable execution through an "in-flight" queue pattern, exponential backoff, and automated dead-letter routing.

## Live Demo
**Operations Dashboard:** [Insert Your Vercel URL Here]

*(Note: The backend is hosted on a free Render tier, which spins down after 15 minutes of inactivity. Please allow 30-60 seconds for the initial API container to boot up when making your first request).*

## System Architecture
The following diagram illustrates the flow of jobs through the system, from the API ingestion point to persistent state storage.

```mermaid
graph LR
    Client --> API[Producer API]
    API --> Redis[(Redis Queue)]
    Redis --> Worker[Worker Service]
    Worker --> DB[(PostgreSQL)]
    Worker --> Prometheus[Prometheus/Metrics]
