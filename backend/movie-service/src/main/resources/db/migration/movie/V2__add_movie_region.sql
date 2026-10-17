-- Optional: movies added before regions existed have none.
alter table movie add column region varchar(20) check (region in ('SINGAPORE', 'INDIA', 'HOLLYWOOD'));

create index movie_region_idx on movie (region);
