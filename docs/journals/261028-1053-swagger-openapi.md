---
title: Swagger and OpenAPI for REST services
date: 2026-10-28
tags: [openapi, swagger, api-documentation, springdoc]
---

# Swagger and OpenAPI for REST services

## Context

The project needed interactive API documentation before testing the microservices through the API Gateway. The service API remains versioned under `/api/v1/**`; `/v3/api-docs` refers to the OpenAPI 3 document format, not a new business API version.

## What happened

- Added Springdoc Swagger UI support to user, device, usage, notification, and insight services.
- Exposed generated OpenAPI JSON at `/v3/api-docs` and interactive documentation at `/swagger-ui.html`.
- Limited generated documentation to `/api/v1/**`, excluding internal and Actuator endpoints.
- Added API title/version metadata and a JWT bearer security scheme.
- Permitted documentation routes through each service's Spring Security configuration.
- Added `OPENAPI_ENABLED` and `SWAGGER_UI_ENABLED` runtime toggles.

## Decisions

- Document only the five services that expose REST controllers. `ingestion-service` remains excluded because it currently runs scheduled ingestion and exposes no HTTP API.
- Keep the business contract at API `v1` while generating an OpenAPI 3 specification.
- Apply JWT security globally so Swagger **Authorize** sends the bearer token, then explicitly mark register, login, refresh, and device-model lookup as public operations.
- Default documentation to enabled for local/test and disable both JSON and UI endpoints in production.

## Verification

- Code review reached 9.7/10 after correcting the Insight service YAML placement and JWT operation requirements.
- `git diff --check` reported no formatting errors in the Swagger-related changes.
- Module build/test verification was still running when this journal entry was written.

## Next

Optionally aggregate all service OpenAPI documents into one Swagger UI hosted by the API Gateway after the per-service documentation is verified at runtime.
