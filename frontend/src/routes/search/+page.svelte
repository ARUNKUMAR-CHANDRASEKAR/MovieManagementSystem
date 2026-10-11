<script lang="ts">
  import { page } from '$app/state';
  import { gql, isAbort } from '$lib/api';
  import { SEARCH_ALL } from '$lib/queries';
  import type { Movie, Page, Person, Role } from '$lib/types';
  import { plural } from '$lib/utils';
  import MovieCard from '$lib/components/MovieCard.svelte';
  import PersonRow from '$lib/components/PersonRow.svelte';

  const query = $derived(page.url.searchParams.get('q')?.trim() ?? '');
  let result = $state<{ movies: Page<Movie>; people: Page<Person> } | null>(null);
  let loading = $state(false);
  let error = $state('');

  $effect(() => {
    const search = query;
    result = null;
    error = '';
    if (!search) return;
    const controller = new AbortController();
    loading = true;
    gql<{ movies: Page<Movie>; people: Page<Person> }>(SEARCH_ALL, { search }, controller.signal)
      .then((data) => (result = data))
      .catch((e) => !isAbort(e) && (error = e.message))
      .finally(() => !controller.signal.aborted && (loading = false));
    return () => controller.abort();
  });

  const byRole = (role: Role) => result?.people.items.filter((p) => p.role === role) ?? [];
</script>

<svelte:head><title>Search · Movie Manager</title></svelte:head>

<h1>Search{query ? `: "${query}"` : ''}</h1>

{#if !query}
  <p class="hint">Type in the search box at the top to find movies, cast and creators.</p>
{:else if loading}
  <p class="status"><span class="spinner"></span> Searching movies, cast and creators…</p>
{:else if error}
  <div class="alert" role="alert">{error}</div>
{:else if result}
  <p class="status">{plural(result.movies.totalCount, 'movie')} and {result.people.totalCount === 1 ? '1 person' : `${result.people.totalCount} people`} found</p>

  <h2>Movies</h2>
  {#if result.movies.items.length}
    <div class="movie-grid">
      {#each result.movies.items as movie (movie.id)}<MovieCard {movie} />{/each}
    </div>
    {#if result.movies.totalCount > result.movies.items.length}
      <p><a href="/?q={encodeURIComponent(query)}">See all {result.movies.totalCount} movies →</a></p>
    {/if}
  {:else}
    <p class="hint">No movies match.</p>
  {/if}

  {#each [['CAST', 'Cast'], ['CREATOR', 'Creators']] as const as [role, label] (role)}
    <h2>{label}</h2>
    {#if byRole(role).length}
      <ul class="card list">
        {#each byRole(role) as person (person.id)}<PersonRow {person} />{/each}
      </ul>
    {:else}
      <p class="hint">No {label.toLowerCase()} match.</p>
    {/if}
  {/each}
  {#if result.people.totalCount > result.people.items.length}
    <p><a href="/people?q={encodeURIComponent(query)}">See all {result.people.totalCount} people →</a></p>
  {/if}
{/if}

<style>
  h2 { margin-top: 1.75rem; }
  .list { list-style: none; padding: 0; margin: 0; }
</style>
