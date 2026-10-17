<script lang="ts">
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { gql, isAbort } from '$lib/api';
  import { MOVIES_SHOWING } from '$lib/queries';
  import { COUNTRIES, parseCountry, type Country } from '$lib/showtimes';
  import type { Movie } from '$lib/types';
  import { plural, queryString } from '$lib/utils';
  import CountryPicker from '$lib/components/CountryPicker.svelte';
  import MovieCard from '$lib/components/MovieCard.svelte';
  import MovieGridSkeleton from '$lib/components/MovieGridSkeleton.svelte';

  const country = $derived(parseCountry(page.url.searchParams.get('country')));
  let movies = $state<Movie[] | null>(null);
  let loading = $state(true);
  let error = $state('');
  let reload = $state(0);

  $effect(() => {
    const variables = { country };
    void reload;
    const controller = new AbortController();
    loading = true;
    error = '';
    gql<{ moviesShowing: Movie[] }>(MOVIES_SHOWING, variables, controller.signal)
      .then((data) => (movies = data.moviesShowing))
      .catch((e) => !isAbort(e) && (error = e.message))
      .finally(() => !controller.signal.aborted && (loading = false));
    return () => controller.abort();
  });

  const choose = (value: Country) => goto(queryString({ country: value }), { replaceState: true, noScroll: true });
</script>

<svelte:head><title>Now Showing · Movie Manager</title></svelte:head>

<div class="toolbar">
  <h1 class="grow">Now showing in {COUNTRIES[country].label}</h1>
</div>

<div class="toolbar">
  <CountryPicker {country} onchange={choose} />
</div>

<p class="status" aria-live="polite">
  {#if loading}
    <span class="spinner"></span> Finding movies showing in {COUNTRIES[country].label}…
  {:else if movies && !error}
    {plural(movies.length, 'movie')} showing in {COUNTRIES[country].label}. Choose one to see locations and times.
  {/if}
</p>

{#if error}
  <div class="alert" role="alert">
    <span>Could not load movies: {error}</span>
    <button class="btn small" onclick={() => reload++}>Try again</button>
  </div>
{:else if !movies}
  <MovieGridSkeleton />
{:else if movies.length === 0}
  <div class="empty card"><p>No movies are showing in {COUNTRIES[country].label} right now.</p></div>
{:else}
  <div class="movie-grid" class:stale={loading}>
    {#each movies as movie (movie.id)}
      <MovieCard {movie} href="/movies/{movie.id}/showtimes{queryString({ country })}" />
    {/each}
  </div>
{/if}

<style>
  .stale { opacity: 0.6; }
</style>
