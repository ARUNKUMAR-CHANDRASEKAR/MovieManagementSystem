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
        SS[showtime-service<br/>gRPC :9093]
    end

    MDB[(PostgreSQL movies :5433<br/>movie, movie_artwork, movie_credit)]
    PDB[(PostgreSQL people :5434<br/>person)]
    SDB[(PostgreSQL showtimes :5435<br/>theatre, showtime, booking, booked_seat)]

    User --> UI
    UI -- "POST /graphql (JSON)" --> Vite
    UI -- "GET /api/movies/{id}/artwork" --> Vite
    Vite --> GW
    GW -- gRPC --> MS
    GW -- gRPC --> PS
    GW -- gRPC --> SS
    MS -- "gRPC: GetPerson<br/>(check person exists)" --> PS
    PS -- "gRPC: DeleteCreditsForPerson<br/>(before deleting a person)" --> MS
    MS -- JPA / Flyway --> MDB
    SS -- "gRPC: GetMovie<br/>(check movie exists before scheduling)" --> MS
    PS -- JPA / Flyway --> PDB
    SS -- JPA / Flyway --> SDB
```

## 2. Request handling inside a service

Each backend layer has one job.

```mermaid
flowchart TD
    A[GraphQL request] --> B[Gateway controller<br/>MovieController / PersonController / ShowtimeController]
    B --> C{Valid id and base64?}
    C -- no --> E1[BadInputException]
    C -- yes --> D[gRPC blocking stub<br/>5 s deadline]
    D --> F[Service gRPC endpoint<br/>MovieGrpcEndpoint / PeopleGrpcEndpoint / ShowtimeGrpcEndpoint]
    F --> G[Domain service<br/>MovieCatalog / PersonDirectory / ShowtimeCatalog]
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

## 3. User journey: search by location and book seats

What the user sees, page by page.

```mermaid
flowchart TD
    A[Movies page<br/>tabs: All / Singapore / Malaysia] -->|choose Singapore| B[Movies showing in Singapore]
    N[Now Showing page<br/>Search by location: Singapore / Malaysia] --> B
    M[Movie page<br/>Book tickets button] --> C
    B -->|choose Jailer| C[Jailer showtimes in Singapore<br/>switch country on the same page]
    C --> D[Locations, one cinema each<br/>Orchard · Yishun · Tampines · Bishan]
    D --> E[Days and show times<br/>with seats left or Sold out]
    E -->|choose 5:30 pm at Orchard| F[Seat map<br/>Available · Selected · Sold]
    F -->|pick up to 8 seats| G[Summary<br/>seats, price per seat, total]
    G -->|name + Proceed to book| H{All seats still free?}
    H -- yes --> I[Confirmation<br/>reference BK-000337, no payment]
    H -- no --> J[Message: already sold<br/>seat map refreshes, other picks kept]
    J --> F
    I -->|Book more seats| F
```

## 4. Seat states on the seat map

```mermaid
stateDiagram-v2
    [*] --> Available
    Available --> Selected: user taps seat
    Selected --> Available: user taps again
    Selected --> Sold: booked (by this user, or by someone else first)
    Available --> Sold: someone else books it
    Sold --> [*]
    note right of Sold
        Grey and disabled.
        The database primary key (showtime_id, seat)
        makes a second sale impossible.
    end note
```

## 5. Sequence: list movies with cast (one batched people call)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as SvelteKit
    participant GW as api-gateway
    participant MS as movie-service
    participant SS as showtime-service
    participant PS as people-service
    participant MDB as movies DB
    participant PDB as people DB

    U->>UI: Type in search box
    Note over UI: Debounce 300 ms.<br/>Abort the older request.<br/>Show "Searching…"
    UI->>GW: POST /graphql movies(search, showingIn, page, size)
    opt showingIn set (location tab)
        GW->>SS: ListMovieIdsShowing(country)
        SS-->>GW: movie ids (none → empty page, stop)
    end
    GW->>MS: ListMovies(search, ids, page, size)
    MS->>MS: Validate page ≥ 0, size 1..100
    MS->>MDB: SELECT … WHERE lower(title) LIKE … [AND id IN (ids)] ORDER BY title
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

## 6. Sequence: upload artwork with progress

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

## 7. Sequence: add a person to a movie (service-to-service gRPC)

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

## 8. Sequence: delete a person (keep data consistent across databases)

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

## 9. Sequence: search by location and book seats

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as SvelteKit
    participant GW as api-gateway
    participant SS as showtime-service
    participant MS as movie-service
    participant SDB as showtimes DB

    U->>UI: Now Showing, location = Singapore
    UI->>GW: moviesShowing(country: SINGAPORE)
    GW->>SS: ListMovieIdsShowing(SINGAPORE)
    SS->>SDB: distinct movie_id of upcoming shows in Singapore theatres
    SS-->>GW: movie ids
    GW->>MS: GetMoviesByIds(ids)
    MS-->>GW: movies
    GW-->>UI: movies (sorted by title)

    U->>UI: Choose Jailer
    UI->>GW: showtimes(movieId, SINGAPORE)
    GW->>SS: ListShowtimes
    SS-->>GW: shows with theatre and seats left
    UI-->>U: Orchard, Yishun, Tampines, Bishan → days → times (local time zone)

    U->>UI: Choose 5:30 pm at Orchard
    UI->>GW: showtime(id) { soldSeats theatre { seatRows seatsPerRow } }
    GW->>SS: GetShowtime
    UI-->>U: Seat map: Available / Selected / Sold

    U->>UI: Pick E6, E7, enter name, Proceed to book
    UI->>GW: bookSeats(showtimeId, [E6, E7], name)
    GW->>SS: BookSeats
    SS->>SS: Check name, 1–8 seats, seats exist, show not started, not already sold
    SS->>SDB: BEGIN, INSERT booking, INSERT booked_seat × 2
    alt All seats free
        SS->>SDB: COMMIT
        SS-->>GW: Booking BK-000337, total
        GW-->>UI: Booking
        UI-->>U: Confirmation with reference
    else A seat was sold a moment ago
        SDB--xSS: Primary key (showtime_id, seat) violated
        SS->>SDB: ROLLBACK (no seat booked)
        SS-->>GW: INVALID_ARGUMENT "Some of these seats were just booked…"
        GW-->>UI: BAD_REQUEST
        UI->>GW: Reload seat map
        UI-->>U: Message, and the seat now shows as Sold
    end
```
