-- A cinema. Every show in it uses the same seat layout: rows A, B, C… with seats 1..seats_per_row.
create table theatre (
    id            bigserial    primary key,
    name          varchar(100) not null,
    country       varchar(20)  not null check (country in ('SINGAPORE', 'MALAYSIA')),
    location      varchar(100) not null,
    seat_rows     integer      not null check (seat_rows between 1 and 26),
    seats_per_row integer      not null check (seats_per_row between 1 and 30)
);

-- movie_id refers to movie-service, which has its own database, so there is no foreign key.
create table showtime (
    id          bigserial   primary key,
    movie_id    bigint      not null,
    theatre_id  bigint      not null references theatre (id),
    starts_at   timestamptz not null,
    price_cents integer     not null check (price_cents > 0)
);

create index showtime_movie_idx on showtime (movie_id, starts_at);

create table booking (
    id            bigserial    primary key,
    showtime_id   bigint       not null references showtime (id),
    customer_name varchar(100) not null,
    total_cents   integer      not null,
    created_at    timestamptz  not null default now()
);

-- The primary key makes double booking impossible: two bookings can never hold the same seat
-- of the same show, even if they are made at the same moment.
create table booked_seat (
    showtime_id bigint     not null references showtime (id),
    seat        varchar(3) not null,
    booking_id  bigint     not null references booking (id),
    primary key (showtime_id, seat)
);
