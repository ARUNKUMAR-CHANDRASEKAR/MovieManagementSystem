<script lang="ts">
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { gql, isAbort } from '$lib/api';
  import { MOVIES } from '$lib/queries';
  import type { Movie, Page } from '$lib/types';
  import { COUNTRIES, COUNTRY_LIST, type Country } from '$lib/showtimes';
  import { debounce, parsePage, plural, queryString } from '$lib/utils';
  import MovieCard from '$lib/components/MovieCard.svelte';
  import MovieGridSkeleton from '$lib/components/MovieGridSkeleton.svelte';
  import Pagination from '$lib/components/Pagination.svelte';

  const PAGE_SIZE = 12;

  // The URL is the source of truth for search and page, so results can be bookmarked and shared.
  const query = $derived(page.url.searchParams.get('q') ?? '');
  const pageNumber = $derived(parsePage(page.url.searchParams.get('page')));
  // Tab: movies showing in a country. Unknown values in the URL fall back to all movies.
  const showing = $derived(COUNTRY_LIST.find((c) => c === page.url.searchParams.get('showing')) ?? null);

  let search = $state(page.url.searchParams.get('q') ?? '');
  let result = $state<Page<Movie> | null>(null);
  const pageCount = $derived(result ? Math.max(1, Math.ceil(result.totalCount / PAGE_SIZE)) : 1);
  let loading = $state(true);
  let error = $state('');
  let reload = $state(0);

  $effect(() => {
    const variables = { search: query, showingIn: showing, page: pageNumber, size: PAGE_SIZE };
    void reload;
    const controller = new AbortController();
    loading = true;
    error = '';
    gql<{ movies: Page<Movie> }>(MOVIES, variables, controller.signal)
      .then((data) => (result = data.movies))
      .catch((e) => !isAbort(e) && (error = e.message))
      .finally(() => !controller.signal.aborted && (loading = false));
    // Cancel this request if the search changes before it finishes.
    return () => controller.abort();
  });

  const navigate = (q: string, c: Country | null, p: number) =>
    goto(queryString({ q: q.trim(), showing: c, page: p }), { replaceState: true, keepFocus: true, noScroll: p === pageNumber });
  const searchLater = debounce((q: string) => navigate(q, showing, 0), 300);
  const where = $derived(showing ? ` showing in ${COUNTRIES[showing].label}` : '');
</script>

<svelte:head><title>Movies · Movie Manager</title></svelte:head>

<div class="toolbar">
  <h1 class="grow">{showing ? `Movies showing in ${COUNTRIES[showing].label}` : 'Movies'}</h1>
  <a class="btn primary" href="/movies/new">+ Add movie</a>
</div>

<div class="toolbar">
  <div class="grow">
    <label for="movie-search" class="visually-hidden">Search movies by title</label>
    <input id="movie-search" type="search" placeholder="Search movies by title…" bind:value={search} oninput={() => searchLater(search)} />
  </div>
  <div class="tabs" role="group" aria-label="Filter by where the movie is showing">
    <button class="btn small" aria-pressed={showing === null} onclick={() => navigate(query, null, 0)}>All</button>
    {#each COUNTRY_LIST as value (value)}
      <button class="btn small" aria-pressed={showing === value} onclick={() => navigate(query, value, 0)}>{COUNTRIES[value].label}</button>
    {/each}
  </div>
</div>

<p class="status" aria-live="polite">
  {#if loading}
    <span class="spinner"></span> {query ? `Searching for "${query}"${where}…` : `Loading movies${where}…`}
  {:else if result && !error}
    {plural(result.totalCount, 'movie')}{where}{query ? ` matching "${query}"` : ''}
  {/if}
</p>

{#if error}
  <div class="alert" role="alert">
    <span>Could not load movies: {error}</span>
    <button class="btn small" onclick={() => reload++}>Try again</button>
  </div>
{:else if !result}
  <MovieGridSkeleton />
{:else if result.items.length === 0 && result.totalCount > 0}
  <!-- The page number in the URL is past the last page, e.g. ?page=99999. -->
  <div class="empty card" role="alert">
    <p><strong>Page not found.</strong> There {pageCount === 1 ? 'is only 1 page' : `are only ${pageCount} pages`} of movies.</p>
    <button class="btn primary" onclick={() => navigate(query, showing, 0)}>Go to first page</button>
  </div>
{:else if result.items.length === 0}
  <div class="empty card">
    {#if query || showing}
      <p>No movies{where}{query ? ` match "${query}"` : ' yet'}.</p>
    {:else}
      <p>No movies yet.</p>
      <a class="btn primary" href="/movies/new">Add the first movie</a>
    {/if}
  </div>
{:else}
  <div class="movie-grid" class:stale={loading}>
    {#each result.items as movie (movie.id)}
      <MovieCard {movie} href={showing ? `/movies/${movie.id}/showtimes${queryString({ country: showing })}` : undefined} />
    {/each}
  </div>
  <Pagination page={pageNumber} size={PAGE_SIZE} total={result.totalCount} disabled={loading} onchange={(p) => navigate(query, showing, p)} />
{/if}

<style>
  .stale { opacity: 0.6; transition: opacity 0.2s; }
  .tabs { display: flex; gap: 0.35rem; flex-wrap: wrap; }
  .tabs [aria-pressed='true'] { background: var(--text); color: var(--bg); border-color: var(--text); }
</style>
