---
title: PostgreSQL multiple-database initialization
date: 2026-10-28
tags: [postgresql, docker, initialization]
---

# PostgreSQL multiple-database initialization

## Context

Docker Compose already declared three service databases and mounted an initialization script, but the referenced script did not exist.

## What Happened

- Added `docker/postgres/init-multiple-databases.sh` to parse `POSTGRES_MULTIPLE_DATABASES`, trim entries, and create missing databases with safely quoted identifiers.
- Made creation idempotent with a catalog existence check and PostgreSQL `\gexec`.
- Wrapped execution in `main()` and propagated `psql` failures explicitly.

## Reflection

PostgreSQL's Docker entrypoint may execute an executable `.sh` file or source a non-executable one. Avoiding global shell-option changes and early process exits keeps both paths safe, while explicit failure propagation prevents a false success message.

## Decisions

- Keep the existing Compose environment contract and database names unchanged.
- Support both sourced and directly executed entrypoint behavior.
- Leave existing PostgreSQL volumes untouched because initialization scripts only run automatically for a fresh data directory.

## Next

Start Docker Desktop and run a fresh, isolated PostgreSQL container test when runtime verification is needed. Static Bash checks, Compose validation, failure simulation, and independent review all passed; the Docker engine was unavailable for the integration test in this session.
