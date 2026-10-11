create table movie (
    id                 bigserial    primary key,
    title              varchar(200) not null,
    release_year       integer,
    genre              varchar(100) not null default '',
    synopsis           text         not null default '',
    artwork_updated_at timestamptz,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now()
);

create index movie_title_lower_idx on movie (lower(title));

-- Kept out of the movie table so listing movies never loads image bytes.
create table movie_artwork (
    movie_id     bigint       primary key references movie (id) on delete cascade,
    content_type varchar(50)  not null,
    data         bytea        not null
);

-- person_id refers to people-service, which has its own database, so there is no foreign key.
create table movie_credit (
    movie_id  bigint not null references movie (id) on delete cascade,
    person_id bigint not null,
    primary key (movie_id, person_id)
);

create index movie_credit_person_idx on movie_credit (person_id);
