# Project Spec — Movie Management System

Sources: the assessment brief (kept out of the repository) and ChatGPT planning chat (https://chatgpt.com/share/6acb7072-abd4-83ec-88ea-06640e12d9c9), captured 2026-10-11.

## Mandatory requirements (from the assessment brief)

Svelte frontend in **TypeScript**, Spring Boot services in **Kotlin**.

### Functionality

1. List movies with artwork.
2. Add, delete and update movies.
3. Upload movie artwork.
4. List cast and creators.
5. Add, delete and update cast and creators.
6. Search movies, cast and creators.
7. Any other functionality we think it should have (our choice — see "Extras").

### Technical

1. At least **2 Spring Boot services**.
2. Service-to-service communication via **gRPC**.
3. Frontend-to-backend communication via **GraphQL**.
4. **Unit tests** with complete test cases, **including edge cases**.

### UX

- Web app must be **responsive** (works on phone and desktop).
- Clear **progress feedback** for: file upload (real upload progress), listing, updating and searching (loading states, success/error messages).

### Submission

1. A **git repository** (this folder is not a git repo yet — run `git init` before the first commit).
2. A **README** covering setup, how to run, and **how AI tools were used**.
3. Possible follow-up walkthrough session: every line must be explainable by the author. Keep the code simple and avoid anything we can't defend.

## Our decisions (from the chat)

- SvelteKit as the Svelte application framework.
- PostgreSQL, one database per service.
- API gateway service exposes GraphQL to the frontend, calls the other services via gRPC.

## Extras (requirement 7 — do only after mandatory parts work)

- Link cast/creators to movies (movie detail shows its people; person detail shows filmography). This is the gRPC call path: gateway/movie-service asks people-service for people details.
- Pagination on lists.

- **Kafka (optional, not started)** for async domain events. Do not replace gRPC with it.
  - Start with one topic `movie.events`, with an `eventType` field (`MOVIE_CREATED`, `MOVIE_UPDATED`, `MOVIE_DELETED`, `ARTWORK_UPLOADED`, `PERSON_CREATED`, `PERSON_UPDATED`).
  - Typed event DTO: `eventId`, `eventType`, `aggregateId`, `occurredAt`, payload.
  - Audit consumer stores events.
  - **Activity History** screen in the frontend shows the audit events.
  - Producer/consumer tests and failure-handling tests.

## Architecture

See the README. Ports: gateway HTTP 8080, movie-service gRPC 9091, people-service gRPC 9092, Postgres 5433 and 5434.
Each service owns its own database. The gateway has no database.

## Status (2026-10-11)

| Requirement | Status |
|---|---|
| List movies with artwork | Done |
| Add, update, delete movies | Done |
| Upload artwork with progress | Done (GraphQL base64 + XHR progress; type checked by bytes) |
| List cast and creators | Done (role filter) |
| Add, update, delete cast and creators | Done |
| Search movies, cast, creators | Done (per list + global /search page) |
| Extra: link people to movies | Done |
| Extra: region tabs (Singapore, India, Hollywood) | Done |
| 2+ Spring Boot services, gRPC between them | Done (gateway, movie-service, people-service) |
| GraphQL frontend to backend | Done |
| Unit tests with edge cases | Done: backend 112, frontend 57 |
| Responsive, progress feedback | Done |
| README (setup, run, AI usage) | Done |
| Git repository | **To do:** `git init`, commit, push |

Not done (optional):
- Kafka audit events and an Activity History screen.
- Integration tests against real Postgres (Testcontainers).
- Running the whole stack in Docker Compose.

## Decisions

- Fields chosen (the brief gives none): Movie = title, releaseYear, genre, synopsis, artwork; Person = name, role (CAST / CREATOR), bio.
- Artwork storage: Postgres `bytea` in `movie_artwork`. Upload goes through GraphQL as base64. Download is `GET /api/movies/{id}/artwork`.
- movie-service owns the movie ↔ person links (`movie_credit`).
- Authentication: not required by the brief — skip.
- Deadline: not stated in the brief.
