<script lang="ts">
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { gql, isAbort } from '$lib/api';
  import { MOVIES } from '$lib/queries';
  import { REGION_LABEL, type Movie, type Page, type Region } from '$lib/types';
  import { debounce, parsePage, plural, queryString } from '$lib/utils';
  import MovieCard from '$lib/components/MovieCard.svelte';
  import MovieGridSkeleton from '$lib/components/MovieGridSkeleton.svelte';
  import Pagination from '$lib/components/Pagination.svelte';

  const PAGE_SIZE = 12;
  const REGIONS = Object.entries(REGION_LABEL) as [Region, string][];

  // The URL is the source of truth for search and page, so results can be bookmarked and shared.
  const query = $derived(page.url.searchParams.get('q') ?? '');
  const pageNumber = $derived(parsePage(page.url.searchParams.get('page')));
  // Unknown values in the URL fall back to all regions.
  const region = $derived(REGIONS.find(([r]) => r === page.url.searchParams.get('region'))?.[0] ?? null);

  let search = $state(page.url.searchParams.get('q') ?? '');
  let result = $state<Page<Movie> | null>(null);
  let loading = $state(true);
  let error = $state('');
  let reload = $state(0);

  $effect(() => {
    const variables = { search: query, region, page: pageNumber, size: PAGE_SIZE };
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

  const navigate = (q: string, r: Region | null, p: number) =>
    goto(queryString({ q: q.trim(), region: r, page: p }), { replaceState: true, keepFocus: true, noScroll: p === pageNumber });
  const searchLater = debounce((q: string) => navigate(q, region, 0), 300);
  const where = $derived(region ? ` from ${REGION_LABEL[region]}` : '');
</script>

<svelte:head><title>Movies · Movie Manager</title></svelte:head>

<div class="toolbar">
  <h1 class="grow">{region ? `${REGION_LABEL[region]} movies` : 'Movies'}</h1>
  <a class="btn primary" href="/movies/new">+ Add movie</a>
</div>

<div class="toolbar">
  <div class="grow">
    <label for="movie-search" class="visually-hidden">Search movies by title</label>
    <input id="movie-search" type="search" placeholder="Search movies by title…" bind:value={search} oninput={() => searchLater(search)} />
  </div>
  <div class="tabs" role="group" aria-label="Filter by region">
    <button class="btn small" aria-pressed={region === null} onclick={() => navigate(query, null, 0)}>All</button>
    {#each REGIONS as [value, label] (value)}
      <button class="btn small" aria-pressed={region === value} onclick={() => navigate(query, value, 0)}>{label}</button>
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
{:else if result.items.length === 0}
  <div class="empty card">
    {#if query || region}
      <p>No movies{where}{query ? ` match "${query}"` : ' yet'}.</p>
    {:else}
      <p>No movies yet.</p>
      <a class="btn primary" href="/movies/new">Add the first movie</a>
    {/if}
  </div>
{:else}
  <div class="movie-grid" class:stale={loading}>
    {#each result.items as movie (movie.id)}
      <MovieCard {movie} />
    {/each}
  </div>
  <Pagination page={pageNumber} size={PAGE_SIZE} total={result.totalCount} disabled={loading} onchange={(p) => navigate(query, region, p)} />
{/if}

<style>
  .stale { opacity: 0.6; transition: opacity 0.2s; }
  .tabs { display: flex; gap: 0.35rem; flex-wrap: wrap; }
  .tabs [aria-pressed='true'] { background: var(--text); color: var(--bg); border-color: var(--text); }
</style>
