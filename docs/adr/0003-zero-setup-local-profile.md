# ADR 0003: Provide H2 locally and PostgreSQL for deployment-shaped tests

- Status: accepted
- Date: 2026-07-15

## Context

The immediate goal is local product testing. Docker is installed on the current
machine, but the developer account cannot access its socket.

## Decision

Make a file-based H2 database in PostgreSQL compatibility mode the default
`local` profile. Keep PostgreSQL 17 as the deployment-shaped database through
the `postgres` profile, Compose, and Testcontainers. Use the same Flyway schema
and Hibernate validation in both.

## Consequences

`./scripts/dev.sh` works without infrastructure. SQL compatibility is checked
locally and PostgreSQL-specific behavior is checked when Docker is available.
H2 is not considered a production database.
