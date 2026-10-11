# Architecture

See the "Architecture" and "Design notes" sections in the [README](../README.md).

## gRPC contracts

`backend/proto-contracts/src/main/proto/`

- `movie.proto`, `MovieService`: movie CRUD and search, artwork upload and download, credits.
- `people.proto`, `PeopleService`: person CRUD and search, and a batch lookup by ids for the gateway.

## Error mapping

| Service throws | gRPC status | GraphQL error type | User sees |
|---|---|---|---|
| `InvalidInputException` | `INVALID_ARGUMENT` | `BAD_REQUEST` | the validation message |
| `NotFoundException` | `NOT_FOUND` | `NOT_FOUND` | the not-found message |
| message too large | `RESOURCE_EXHAUSTED` | `BAD_REQUEST` | "The upload is too large" |
| service down or slow | `UNAVAILABLE` / `DEADLINE_EXCEEDED` | `INTERNAL_ERROR` | "A backend service is unavailable…" |
| anything else | `INTERNAL` | `INTERNAL_ERROR` | a generic message, logged on the server |

## Databases

- movie-service: `movie` (with optional `region`: SINGAPORE, INDIA, HOLLYWOOD), `movie_artwork` (image bytes, kept apart so lists never load them), `movie_credit`.
- people-service: `person`.

Flyway migrations are in each service's `src/main/resources/db/migration`.
