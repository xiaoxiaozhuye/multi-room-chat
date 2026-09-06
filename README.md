# Multi-room chat backend

Java 17 / Spring Boot 3 modular-monolith backend. Domain code is separated into
`auth`, `room`, `member`, `message`, `audit`, and `websocket`; cross-cutting code
lives in `security`, `common`, and `infrastructure`.

## Run locally

1. The development profile targets the `postgres` database at `192.168.186.131:5432` as user `postgres`; set the required `CHAT_DB_PASSWORD` environment variable before running. Use `CHAT_DB_URL` to target a dedicated application database.
2. Start Redis (default `localhost:6379`).
3. Run `D:\u_soft\apache-maven-3.9.7\bin\mvn.cmd spring-boot:run`.

The `dev` profile is the default. It runs the versioned migrations in
`src/main/resources/db/migration` with Flyway. Environment variables in
`application-dev.yml` override database, Redis, JWT and audit timeout values.

## Test and coverage

Run `D:\u_soft\apache-maven-3.9.7\bin\mvn.cmd test`. Tests use the `test`
profile's in-memory H2 datasource and do not require PostgreSQL or Redis. JaCoCo
HTML output is generated at `target/site/jacoco/index.html`.

## Layering rule

`controller -> service -> mapper -> PostgreSQL/Redis`. Controllers use request
and response DTOs only; services own transactional business rules; entities map
to persisted rows; mapper interfaces live in `infrastructure.mapper`. Domain
modules must not call another module's mapper directly.

Redis key ownership, recovery and degradation behaviour are documented in
[docs/redis-infrastructure.md](docs/redis-infrastructure.md).
