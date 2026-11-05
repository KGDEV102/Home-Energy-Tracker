# Authentication

## Current implementation

`POST /api/v1/auth/register` is public and returns HTTP 201 with
`SuccessResponse<UserDto>` (user profile only, no token, password, or hash).
Send `firstName`, `lastName`, `email`, `password`, and optional `address`.
Email is trimmed and lowercased before duplicate checking and saving.
Passwords require 8-72 characters and at most 72 UTF-8 bytes; they are hashed
with the existing BCrypt `PasswordEncoder` and stored in `password_hash`.
Invalid input returns 400; an existing email returns 409, including concurrent
registrations rejected by the database email unique constraint.

Via the gateway, use `http://localhost:8080/api/v1/auth/register`.
Directly, use `http://localhost:8081/api/v1/auth/register`.

Registration does not issue a JWT. Login and refresh are implemented; public
discovery/JWKS endpoints are not provided. Access-token lifetime is configured
by `JWT_EXPIRATION_MILLIS` (default 900000, or 15 minutes).

## Configuration

Set these environment variables before starting the service:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: existing PostgreSQL configuration.
- `JWT_PRIVATE_KEY_BASE64`: Base64 DER of an RSA private key in PKCS#8 format.
- `JWT_PUBLIC_KEY_BASE64`: Base64 DER of the matching RSA public key in X.509 format.
- `JWT_EXPIRATION_MILLIS`: access-token lifetime in milliseconds (default 900000, or 15 minutes).
- `USER_SERVICE_INTERNAL_TOKEN`: a strong shared service credential, configured
  identically in user-service and notification-service. Internal contact lookups
  are denied when it is unset. Use a secret manager and HTTPS/private networking;
  do not expose `/internal/**` through the public gateway.

Use an RSA key pair of at least 2048 bits. Do not commit either private key or
service credential. Key values must contain Base64 only, without PEM headers.
Example key generation with OpenSSL (run in a secure location outside Git):

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl pkcs8 -topk8 -nocrypt -in private.pem -outform DER -out private.der
openssl pkey -in private.pem -pubout -outform DER -out public.der
```

Base64-encode the DER files to populate the environment variables.

## Endpoints

`POST /api/v1/auth/register` (201):

```json
{
  "firstName": "Kiet",
  "lastName": "Nguyen",
  "email": "kiet@example.com",
  "password": "a-long-password",
  "address": "Bangkok"
}
```

`POST /api/v1/auth/login` (200):

```json
{ "email": "kiet@example.com", "password": "a-long-password" }
```

`/login` returns a `SuccessResponse` whose `data` contains the user profile,
`accessToken`, `tokenType` (`Bearer`), and `expiresIn` (seconds). It also sets
an HttpOnly, SameSite=Strict `refresh_token` cookie. Registration returns only
the user profile. Password hashes are never included.

`POST /api/v1/auth/refresh` reads the `refresh_token` cookie, rotates it, and
returns a new access token while setting its replacement cookie.
Wrong credentials, disabled users, and profiles without passwords return 401.
Duplicate registration returns 409. Registration passwords require 8–72 characters
and at most 72 UTF-8 bytes (BCrypt limit).

Send `Authorization: Bearer <accessToken>` for GET/PATCH/DELETE
`/api/v1/users/me`. The user ID is always read from the verified JWT `sub`, so
clients cannot select another user's profile through a path ID. The old
`GET`, `PATCH`, and `DELETE /api/v1/users/{id}` endpoints are no longer exposed;
clients must migrate those calls to the corresponding `/api/v1/users/me`
endpoint. The old `POST /api/v1/users` endpoint is also no longer exposed; new
accounts must use `/api/v1/auth/register`.

`usage-service` and `notification-service` read the same
`JWT_PUBLIC_KEY_BASE64` environment variable and verify access tokens locally.
They do not require an issuer or JWKS endpoint. The shared value is a Base64-only
X.509 DER public key, without PEM headers.

The gateway must route `/api/v1/auth/**` to user-service, permit register/login,
and forward `Authorization` on protected requests. Gateway routing is not configured
by this change.

## Existing users and limitations

Flyway V2 adds nullable `password_hash` without modifying existing user data.
Existing accounts cannot log in until a password is set through a future password
setup/reset flow; there is no default password. Password reset, login throttling,
and immediate access-token revocation are not implemented. Disabling a user
prevents new login, but already issued access tokens remain valid until expiration.

Run `gradle test` in user-service. The authentication tests cover BCrypt, RSA
signing, JWT verification, refresh-token rotation, and security filters. The
`UserControllerIntegrationTest` uses a real PostgreSQL Testcontainer and real JWT
filter chain to verify the authenticated `/api/v1/users/me` contract; it requires
Docker.
