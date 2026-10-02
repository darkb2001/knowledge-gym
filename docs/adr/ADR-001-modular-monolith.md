# ADR-001: Modular monolith for Knowledge Gym

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

Knowledge Gym has one product boundary, one operational owner, and a small deployment
budget. Splitting content, learning, blog automation, and identity into networked
services would add deployment, tracing, and data-consistency costs before there is a
scaling requirement.

## Decision

Keep the backend as a modular monolith with Gradle modules:

- `kg-core` contains domain models, ports, and application use cases.
- `kg-infrastructure` contains persistence, messaging, external adapters, and config.
- `kg-agent` contains scheduled collector/writer agents.
- `kg-presentation` contains the Spring Boot entry point and HTTP adapters.

Modules communicate through ports and use cases rather than reaching into another
module's persistence implementation. The transactional outbox remains the boundary
for Kafka delivery. External cron triggers use the internal HTTP contract and
`app.cron.mode` prevents duplicate embedded/external jobs.

## Consequences

This keeps local development and deployment simple and preserves transactional
consistency. It requires discipline around module dependencies and does not provide
independent scaling; those are acceptable trade-offs until measured load justifies a
service split.
