# API documentation with OpenAPI and Swagger UI

The project generates OpenAPI 3 documentation from Spring MVC controllers with Springdoc. Documentation is limited to public API paths under `/api/v1/**`; internal and Actuator endpoints are excluded.

## Local documentation URLs

After running `docker compose up --build`, use the default host ports below:

| Service | Swagger UI | OpenAPI JSON |
| --- | --- | --- |
| User | <http://localhost:8081/swagger-ui.html> | <http://localhost:8081/v3/api-docs> |
| Device | <http://localhost:8082/swagger-ui.html> | <http://localhost:8082/v3/api-docs> |
| Usage | <http://localhost:8084/swagger-ui.html> | <http://localhost:8084/v3/api-docs> |
| Notification | <http://localhost:8085/swagger-ui.html> | <http://localhost:8085/v3/api-docs> |
| Insight | <http://localhost:8087/swagger-ui.html> | <http://localhost:8087/v3/api-docs> |

The Insight service listens on port `8086` inside its container, but Docker Compose maps it to port `8087` on the host. Values in `.env` can override these default host ports.

`ingestion-service` is not listed because it currently runs scheduled ingestion jobs and does not expose REST controllers. Adding Springdoc to it would only produce empty API documentation.

## Test authenticated endpoints

1. Open the User Swagger UI and call `POST /api/v1/auth/register` if a test user does not exist.
2. Call `POST /api/v1/auth/login` and copy the returned access token.
3. Open the Swagger UI of the service to test and select **Authorize**.
4. Enter the raw access token. Swagger UI adds the `Bearer` prefix automatically.
5. Use **Try it out** to call the protected endpoint.

Registration, login, refresh-token, and public device-model operations are documented without a JWT requirement. Other public API operations use the `bearerAuth` security scheme.

Each service currently has its own Swagger UI. The documentation is not yet aggregated into a single UI at the API Gateway.

## Enable or disable documentation

The following environment variables control documentation at runtime:

```env
OPENAPI_ENABLED=true
SWAGGER_UI_ENABLED=true
```

- `OPENAPI_ENABLED` controls `/v3/api-docs`.
- `SWAGGER_UI_ENABLED` controls the interactive Swagger UI.
- Both default to `true` for local development and testing.

For production, set both values in the deployment environment or secret configuration:

```env
OPENAPI_ENABLED=false
SWAGGER_UI_ENABLED=false
```

Do not rely only on hiding Swagger UI: disable both the UI and the OpenAPI JSON endpoint when production documentation should not be public.
