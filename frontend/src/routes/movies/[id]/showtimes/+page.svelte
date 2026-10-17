<script lang="ts">
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { ApiError, gql, isAbort } from '$lib/api';
  import { MOVIE_SHOWTIMES } from '$lib/queries';
  import { COUNTRIES, formatMoney, formatTime, groupShowtimes, parseCountry, type Country, type Showtime } from '$lib/showtimes';
  import type { Movie } from '$lib/types';
  import { plural, queryString } from '$lib/utils';
  import CountryPicker from '$lib/components/CountryPicker.svelte';

  const FEW_SEATS = 10;

  const id = $derived(page.params.id!);
  const country = $derived(parseCountry(page.url.searchParams.get('country')));
  let movie = $state<Movie | null>(null);
  let shows = $state<Showtime[] | null>(null);
  let loading = $state(true);
  let error = $state<ApiError | null>(null);
  let reload = $state(0);

  $effect(() => {
    const variables = { movieId: id, country };
    void reload;
    const controller = new AbortController();
    loading = true;
    error = null;
    gql<{ movie: Movie; showtimes: Showtime[] }>(MOVIE_SHOWTIMES, variables, controller.signal)
      .then((data) => {
        movie = data.movie;
        shows = data.showtimes;
      })
      .catch((e) => !isAbort(e) && (error = e))
      .finally(() => !controller.signal.aborted && (loading = false));
    return () => controller.abort();
  });

  const groups = $derived(groupShowtimes(shows ?? []));
  const choose = (value: Country) => goto(queryString({ country: value }), { replaceState: true, noScroll: true });
</script>

<svelte:head><title>{movie ? `${movie.title} showtimes` : 'Showtimes'} · Movie Manager</title></svelte:head>

<a href="/showing{queryString({ country })}" class="hint">← Now showing</a>

{#if error}
  <div class="alert" role="alert">
    <span>{error.type === 'NOT_FOUND' || error.type === 'BAD_REQUEST' ? 'This movie does not exist.' : error.message}</span>
    {#if error.type !== 'NOT_FOUND' && error.type !== 'BAD_REQUEST'}<button class="btn small" onclick={() => reload++}>Try again</button>{/if}
  </div>
{:else}
  <div class="head">
    {#if movie?.artworkUrl}<img src={movie.artworkUrl} alt="" />{/if}
    <div>
      <h1>{movie?.title ?? 'Loading…'}</h1>
      {#if movie}<p class="hint">{[movie.releaseYear, movie.genre].filter(Boolean).join(' · ')} · <a href="/movies/{movie.id}">Movie details</a></p>{/if}
    </div>
  </div>

  <div class="toolbar"><CountryPicker {country} onchange={choose} /></div>

  <p class="status" aria-live="polite">
    {#if loading}
      <span class="spinner"></span> Loading showtimes in {COUNTRIES[country].label}…
    {:else if shows}
      {plural(groups.length, 'location')} showing this movie in {COUNTRIES[country].label}. Times are local.
    {/if}
  </p>

  {#if shows && groups.length === 0 && !loading}
    <div class="empty card">
      <p>Not showing in {COUNTRIES[country].label} right now.</p>
      <a class="btn" href="/showing{queryString({ country })}">See what is showing</a>
    </div>
  {/if}

  <div class="theatres" class:stale={loading}>
    {#each groups as group (group.theatre.id)}
      <section class="card panel">
        <h2>{group.theatre.location}</h2>
        <p class="hint">{group.theatre.name}</p>
        {#each group.days as day (day.key)}
          <h3>{day.label}</h3>
          <div class="slots">
            {#each day.shows as show (show.id)}
              {#if show.seatsLeft > 0}
                <a class="slot btn" href="/book/{show.id}" aria-label="{formatTime(show.startsAt, country)}, {show.seatsLeft} seats left, {formatMoney(show.priceCents, show.currency)}">
                  <strong>{formatTime(show.startsAt, country)}</strong>
                  <span class:few={show.seatsLeft <= FEW_SEATS}>{show.seatsLeft <= FEW_SEATS ? `Only ${show.seatsLeft} left` : `${show.seatsLeft} seats`}</span>
                </a>
              {:else}
                <span class="slot btn" aria-disabled="true"><strong>{formatTime(show.startsAt, country)}</strong><span>Sold out</span></span>
              {/if}
            {/each}
          </div>
        {/each}
      </section>
    {/each}
  </div>
{/if}

<style>
  .head { display: flex; gap: 1rem; align-items: center; margin: 1rem 0; }
  .head img { width: 64px; aspect-ratio: 2 / 3; object-fit: cover; border-radius: 6px; }
  .head h1 { margin: 0; }
  .head p { margin: 0.25rem 0 0; }
  .theatres { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); }
  .stale { opacity: 0.6; }
  h2 { margin: 0; font-size: 1.2rem; }
  h3 { font-size: 0.9rem; margin: 1rem 0 0.5rem; color: var(--muted); }
  .slots { display: flex; flex-wrap: wrap; gap: 0.5rem; }
  .slot { flex-direction: column; gap: 0; padding: 0.4rem 0.75rem; min-width: 5.5rem; line-height: 1.25; }
  .slot span { font-size: 0.75rem; color: var(--muted); font-weight: 400; }
  .slot .few { color: var(--accent); }
  .slot[aria-disabled='true'] { opacity: 0.5; cursor: not-allowed; }
</style>
