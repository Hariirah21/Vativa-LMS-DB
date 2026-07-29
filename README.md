# Vativa LMS Backend

Spring Boot backend for the Vativa LMS application.

## Configuration

Provide these environment variables before starting the application:

- `VATIVA_DB_URL`
- `VATIVA_DB_USERNAME`
- `VATIVA_DB_PASSWORD`
- `VATIVA_RESEND_API_KEY`
- `VATIVA_RESEND_FROM_EMAIL`
- `VATIVA_JWT_SECRET`

Optional environment variables and their defaults are documented in
`src/main/resources/application.properties`.

## Verify

```powershell
.\mvnw.cmd clean test
```

## Run

```powershell
.\mvnw.cmd spring-boot:run
```
