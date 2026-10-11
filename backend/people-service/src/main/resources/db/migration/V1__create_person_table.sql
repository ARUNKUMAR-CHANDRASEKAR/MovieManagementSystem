create table person (
    id         bigserial    primary key,
    name       varchar(150) not null,
    role       varchar(20)  not null check (role in ('CAST', 'CREATOR')),
    bio        text         not null default '',
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create index person_name_lower_idx on person (lower(name));
