# Diagrams

GitHub renders these Mermaid diagrams automatically.

## 1. System flow

How a request travels through the system.

```mermaid
flowchart LR
    User([User])

    subgraph Browser
        UI[SvelteKit app<br/>TypeScript]
    end

    subgraph Dev server
        Vite[Vite proxy :5173<br/>forwards /graphql and /api]
    end

    subgraph Backend
        GW[api-gateway :8080<br/>GraphQL + artwork GET]
        MS[movie-service<br/>gRPC :9091]
        PS[people-service<br/>gRPC :9092]
    end

    MDB[(PostgreSQL movies :5433<br/>movie, movie_artwork, movie_credit)]
    PDB[(PostgreSQL people :5434<br/>person)]

    User --> UI
    UI -- "POST /graphql (JSON)" --> Vite
    UI -- "GET /api/movies/{id}/artwork" --> Vite
    Vite --> GW
    GW -- gRPC --> MS
    GW -- gRPC --> PS
    MS -- "gRPC: GetPerson<br/>(check person exists)" --> PS
    PS -- "gRPC: DeleteCreditsForPerson<br/>(before deleting a person)" --> MS
    MS -- JPA / Flyway --> MDB
    PS -- JPA / Flyway --> PDB
```

## 2. Request handling inside a service

Each backend layer has one job.

```mermaid
flowchart TD
    A[GraphQL request] --> B[Gateway controller<br/>MovieController / PersonController]
    B --> C{Valid id and base64?}
    C -- no --> E1[BadInputException]
    C -- yes --> D[gRPC blocking stub<br/>5 s deadline]
    D --> F[Service gRPC endpoint<br/>MovieGrpcEndpoint / PeopleGrpcEndpoint]
    F --> G[Domain service<br/>MovieCatalog / PersonDirectory]
    G --> H{Business rules OK?}
    H -- no --> E2[InvalidInputException / NotFoundException]
    H -- yes --> I[Spring Data repository]
    I --> J[(PostgreSQL)]
    J --> K[Entity mapped to protobuf message]
    K --> L[Gateway maps message to GraphQL view]
    L --> M[JSON response]

    E2 --> N[respond helper: INVALID_ARGUMENT / NOT_FOUND]
    N --> O[GraphQlErrors: BAD_REQUEST / NOT_FOUND]
    E1 --> O
    O --> P[Error JSON with a user-friendly message]
```

## 3. Sequence: list movies with cast (one batched people call)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as SvelteKit
    participant GW as api-gateway
    participant MS as movie-service
    participant PS as people-service
    participant MDB as movies DB
    participant PDB as people DB

    U->>UI: Type in search box
    Note over UI: Debounce 300 ms.<br/>Abort the older request.<br/>Show "Searching…"
    UI->>GW: POST /graphql movies(search, region, page, size)
    GW->>MS: ListMovies(search, region, page, size)
    MS->>MS: Validate page ≥ 0, size 1..100
    MS->>MDB: SELECT … WHERE lower(title) LIKE … [AND region = ?] ORDER BY title
    MDB-->>MS: Movies + credit person ids
    MS-->>GW: MoviePage
    opt Query asks for Movie.people
        GW->>PS: GetPeopleByIds(all ids on the page)
        Note over GW,PS: @BatchMapping makes ONE call per response, not one per movie
        PS->>PDB: SELECT … WHERE id IN (…)
        PDB-->>PS: People
        PS-->>GW: PersonList
    end
    GW-->>UI: { items, totalCount, page, size }
    UI-->>U: Movie grid + "N movies matching …"
```

## 4. Sequence: upload artwork with progress

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as SvelteKit
    participant GW as api-gateway
    participant MS as movie-service
    participant MDB as movies DB

    U->>UI: Choose image file
    UI->>UI: validateArtwork (type, not empty, ≤ 5 MB)
    alt Invalid file
        UI-->>U: Inline error, nothing sent
    else Valid file
        U->>UI: Click Upload
        UI->>UI: FileReader → base64 ("Reading file…")
        UI->>GW: XHR POST /graphql uploadArtwork(movieId, dataBase64)
        loop While bytes are sent
            UI-->>U: Progress bar "Uploading… 45%"
        end
        UI-->>U: "Saving artwork…"
        GW->>GW: Base64 decode (invalid → BAD_REQUEST)
        GW->>MS: UploadArtwork(movieId, bytes)
        MS->>MS: Movie exists? Size ≤ 5 MB?
        MS->>MS: detectImageType(bytes) by magic numbers
        alt Not a JPEG / PNG / GIF / WebP
            MS-->>GW: INVALID_ARGUMENT
            GW-->>UI: BAD_REQUEST "Artwork must be …"
            UI-->>U: Error message
        else Real image
            MS->>MDB: Upsert movie_artwork, set movie.artwork_updated_at
            MS-->>GW: Movie (artwork_updated_at_ms)
            GW-->>UI: artworkUrl = /api/movies/{id}/artwork?v={time}
            UI-->>U: Toast "Artwork uploaded", new image shown
        end
    end

    Note over UI,GW: Later, the browser loads the image
    UI->>GW: GET /api/movies/{id}/artwork?v={time}
    GW->>MS: GetArtwork(id)
    MS->>MDB: SELECT content_type, data
    MS-->>GW: Artwork
    GW-->>UI: Image bytes, Cache-Control immutable, nosniff
```

## 5. Sequence: add a person to a movie (service-to-service gRPC)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as SvelteKit
    participant GW as api-gateway
    participant MS as movie-service
    participant PS as people-service

    U->>UI: Search person, click Add
    UI->>GW: addCredit(movieId, personId)
    GW->>MS: AddCredit(movieId, personId)
    MS->>MS: Load movie (missing → NOT_FOUND)
    MS->>PS: GetPerson(personId)
    alt Person exists
        PS-->>MS: Person
        MS->>MS: personIds.add(personId) (a set, so duplicates are ignored)
        MS-->>GW: Movie with person ids
        GW->>PS: GetPeopleByIds(ids)
        PS-->>GW: People
        GW-->>UI: Movie.people
        UI-->>U: List updated, toast "<name> added"
    else Person not found
        PS-->>MS: NOT_FOUND
        MS-->>GW: NOT_FOUND "Person N not found"
        GW-->>UI: NOT_FOUND error
        UI-->>U: Error toast
    else people-service down
        PS--xMS: UNAVAILABLE
        MS-->>GW: UNAVAILABLE (status passed on)
        GW-->>UI: "A backend service is unavailable…"
        UI-->>U: Error toast
    end
```

## 6. Sequence: delete a person (keep data consistent across databases)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as SvelteKit
    participant GW as api-gateway
    participant PS as people-service
    participant MS as movie-service
    participant PDB as people DB
    participant MDB as movies DB

    U->>UI: Click Delete
    UI-->>U: confirm("Also removed from N movies…")
    U->>UI: OK
    UI->>GW: deletePerson(id)
    GW->>PS: DeletePerson(id)
    PS->>PDB: Find person (missing → NOT_FOUND)
    PS->>MS: DeleteCreditsForPerson(id)
    alt movie-service OK
        MS->>MDB: DELETE FROM movie_credit WHERE person_id = id
        MS-->>PS: Empty
        PS->>PDB: DELETE person
        PS-->>GW: Empty
        GW-->>UI: true
        UI-->>U: Toast, go to people list
    else movie-service down
        MS--xPS: UNAVAILABLE
        Note over PS: Person is NOT deleted, so<br/>no movie points at a missing person
        PS-->>GW: UNAVAILABLE
        GW-->>UI: "A backend service is unavailable…"
        UI-->>U: Error toast, person kept
    end
```
