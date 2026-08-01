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

## Run locally without PostgreSQL

The `local` profile is the default and uses a persistent embedded H2 database
stored under `local-data/`. It is intended only for local development.

```powershell
mvn.cmd spring-boot:run
```

## Run with Neon

The Neon launcher contains no password. It prompts for the current database
password and keeps it only in the backend process environment.

```powershell
.\run-backend-neon.ps1
```
