# Architecture

See the "Architecture" and "Design notes" sections in the [README](../README.md).

## gRPC contracts

`backend/proto-contracts/src/main/proto/`

- `movie.proto`, `MovieService`: movie CRUD and search, artwork upload and download, credits.
- `people.proto`, `PeopleService`: person CRUD and search, and a batch lookup by ids for the gateway.
- `showtime.proto`, `ShowtimeService`: theatres, movies showing per country, shows of a movie, seat map, scheduling and booking.

## Error mapping

| Service throws | gRPC status | GraphQL error type | User sees |
|---|---|---|---|
| `InvalidInputException` | `INVALID_ARGUMENT` | `BAD_REQUEST` | the validation message |
| `NotFoundException` | `NOT_FOUND` | `NOT_FOUND` | the not-found message |
| message too large | `RESOURCE_EXHAUSTED` | `BAD_REQUEST` | "The upload is too large" |
| service down or slow | `UNAVAILABLE` / `DEADLINE_EXCEEDED` | `INTERNAL_ERROR` | "A backend service is unavailable…" |
| anything else | `INTERNAL` | `INTERNAL_ERROR` | a generic message, logged on the server |

## Databases

- showtime-service: `theatre` (8 sample cinemas from migration V2), `showtime`, `booking`, `booked_seat` (primary key `(showtime_id, seat)` prevents double booking).
- movie-service: `movie` (the old `region` column was dropped by migration V3), `movie_artwork` (image bytes, kept apart so lists never load them), `movie_credit`.
- people-service: `person`.

Flyway migrations are in each service's `src/main/resources/db/migration/<service>`.
