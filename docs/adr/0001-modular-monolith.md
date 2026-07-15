# ADR 0001: Use a modular monolith

- Status: accepted
- Date: 2026-07-15

## Context

The first release needs a complete document workflow, strong tests, and clear
portfolio architecture without distributed-system overhead.

## Decision

Use one Spring Boot process with four Maven modules and a bounded in-process
worker. Keep ports for dispatch, persistence, storage, and document processing.

## Consequences

Local development and transactions stay simple. Module ownership remains
visible. Workers can be separated later, but this release has process-local
capacity and no horizontal job coordination.
