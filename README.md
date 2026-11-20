# OneTicket Payments

Payments is the Kotlin and Spring Boot microservice responsible for payment-related functionality in OneTicket.

## Requirements

- JDK 25
- Docker Compose and the central `OneTicket-Main` repository for PostgreSQL

## Configuration

Payments reads its configuration from Spring Boot properties. Environment variables override the local defaults where one is defined.

| Environment variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `SERVER_PORT` | No | `8082` | HTTP port used by the Payments application. |
| `SPRING_DATASOURCE_URL` | No | `jdbc:postgresql://localhost:5433/payments_db` | JDBC URL of the Payments database. Use the Compose hostname and container port when running in Docker. |
| `SPRING_DATASOURCE_USERNAME` | No | `payments` | PostgreSQL account used by Payments. |
| `SPRING_DATASOURCE_PASSWORD` | Yes | None | Password for the Payments PostgreSQL account. Do not commit this value. |

The central Compose project provisions a dedicated `payments_db` database and `payments` account in its PostgreSQL container. Its password is supplied through `PAYMENTS_DB_PASSWORD` in the central repository's untracked `.env` file. Spring Boot does not load `.env` files by itself: Compose passes the value to the container, while a local `bootRun` needs `SPRING_DATASOURCE_PASSWORD` set in the run configuration or shell.

## Run locally

Start PostgreSQL from `OneTicket-Main`, then set the datasource password in PowerShell and run Payments from this repository:

```powershell
$env:SPRING_DATASOURCE_PASSWORD = "<value of PAYMENTS_DB_PASSWORD from OneTicket-Main/.env>"
./gradlew.bat bootRun
```
