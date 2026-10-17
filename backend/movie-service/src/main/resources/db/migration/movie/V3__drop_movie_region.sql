-- Region (where a film comes from) is replaced by location: where it is showing, owned by showtime-service.
drop index movie_region_idx;
alter table movie drop column region;
