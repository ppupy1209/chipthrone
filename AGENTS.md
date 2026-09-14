# chipthrone — agent instructions

## Run

- Verify `./gradlew --version` reports JDK 21; the system default Java may be 8.
- Backend: `cd backend && ./gradlew test`
- Backend local run: `cd backend && ./gradlew bootRun`
- Frontend: `cd frontend && npm run build`; `npm test`; `npm run lint`
- Full local stack: `docker compose up --build`

## Verify

- Run backend tests for backend changes.
- Run frontend build, test, and lint for frontend changes.
- The local services use ports 5173 (frontend) and 8080 (backend).
- Do not run `docker compose down -v`; it removes local data.
- Before committing, verify this repository’s local personal Git identity. Do not change global Git settings or credential helpers.
