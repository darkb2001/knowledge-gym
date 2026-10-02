# ADR-004: Infrastructure, backup, and email boundaries

- **Status:** Accepted with staged rollout
- **Date:** 2026-10-02

## Context

The production target is a single unprivileged Proxmox LXC with a constrained memory
and disk budget. PostgreSQL and Garage contain durable application data; Elasticsearch
and Kafka are rebuildable. Email delivery and offsite backup must not turn the local
node into a larger, fragile platform.

## Decision

- Run the application stack locally in LXC 102 behind Nginx and Cloudflare Tunnel.
- Keep PostgreSQL and Garage local, but copy their data offsite with restic/B2.
- Back up configuration and secrets separately; perform a real restore drill before
  declaring production durable.
- Treat PBS as an optional infrastructure snapshot, not as the offsite source of truth.
- Use Garage as the S3-compatible object store for images, avatars, and exports.
- Keep Gmail/Brevo as the production email fallback until a Mailu deployment has been
  tested for DNS, reputation, and deliverability.
- Retain Elasticsearch indices with an explicit retention job; do not rely on an
  unbounded local disk.

## Consequences

The baseline remains cheap and operable on the existing node. Recovery requires a
restore procedure rather than just a snapshot, and Garage backup increases B2 storage
usage. Mailu/PBS/Lambda remain independently deployable and do not block the core
application release.
