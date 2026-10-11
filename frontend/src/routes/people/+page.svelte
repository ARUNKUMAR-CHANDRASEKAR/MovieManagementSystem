<script lang="ts">
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { gql, isAbort } from '$lib/api';
  import { PEOPLE } from '$lib/queries';
  import type { Page, Person, Role } from '$lib/types';
  import { debounce, parsePage, queryString } from '$lib/utils';
  import Pagination from '$lib/components/Pagination.svelte';
  import PersonRow from '$lib/components/PersonRow.svelte';

  const PAGE_SIZE = 20;
  const FILTERS: { label: string; role: Role | '' }[] = [
    { label: 'All', role: '' },
    { label: 'Cast', role: 'CAST' },
    { label: 'Creators', role: 'CREATOR' }
  ];

  const query = $derived(page.url.searchParams.get('q') ?? '');
  const role = $derived((page.url.searchParams.get('role') ?? '') as Role | '');
  const pageNumber = $derived(parsePage(page.url.searchParams.get('page')));

  let search = $state(page.url.searchParams.get('q') ?? '');
  let result = $state<Page<Person> | null>(null);
  let loading = $state(true);
  let error = $state('');
  let reload = $state(0);

  $effect(() => {
    const variables = {
      search: query,
      role: role === 'CAST' || role === 'CREATOR' ? role : null,
      page: pageNumber,
      size: PAGE_SIZE
    };
    void reload;
    const controller = new AbortController();
    loading = true;
    error = '';
    gql<{ people: Page<Person> }>(PEOPLE, variables, controller.signal)
      .then((data) => (result = data.people))
      .catch((e) => !isAbort(e) && (error = e.message))
      .finally(() => !controller.signal.aborted && (loading = false));
    return () => controller.abort();
  });

  const navigate = (q: string, r: string, p: number) =>
    goto(queryString({ q: q.trim(), role: r, page: p }), { replaceState: true, keepFocus: true, noScroll: true });
  const searchLater = debounce((q: string) => navigate(q, role, 0), 300);
  const noun = $derived(role === 'CAST' ? 'cast member' : role === 'CREATOR' ? 'creator' : 'person');
</script>

<svelte:head><title>Cast &amp; Creators · Movie Manager</title></svelte:head>

<div class="toolbar">
  <h1 class="grow">Cast &amp; Creators</h1>
  <a class="btn primary" href="/people/new">+ Add person</a>
</div>

<div class="toolbar">
  <div class="grow">
    <label for="people-search" class="visually-hidden">Search people by name</label>
    <input id="people-search" type="search" placeholder="Search by name…" bind:value={search} oninput={() => searchLater(search)} />
  </div>
  <div class="filters" role="group" aria-label="Filter by role">
    {#each FILTERS as filter (filter.role)}
      <button class="btn small" aria-pressed={role === filter.role} onclick={() => navigate(query, filter.role, 0)}>{filter.label}</button>
    {/each}
  </div>
</div>

<p class="status" aria-live="polite">
  {#if loading}
    <span class="spinner"></span> {query ? `Searching for "${query}"…` : 'Loading…'}
  {:else if result && !error}
    {result.totalCount === 1 ? `1 ${noun}` : `${result.totalCount} ${noun === 'person' ? 'people' : noun + 's'}`}{query ? ` matching "${query}"` : ''}
  {/if}
</p>

{#if error}
  <div class="alert" role="alert">
    <span>Could not load people: {error}</span>
    <button class="btn small" onclick={() => reload++}>Try again</button>
  </div>
{:else if !result}
  <ul class="card list" aria-hidden="true">
    {#each Array(6) as _, i (i)}
      <li style="padding: 0.9rem"><div class="skeleton" style="height: 1.2rem; width: {40 + ((i * 17) % 40)}%"></div></li>
    {/each}
  </ul>
{:else if result.items.length === 0}
  <div class="empty card">
    <p>{query ? `No ${noun === 'person' ? 'people' : noun + 's'} match "${query}".` : `No ${noun === 'person' ? 'people' : noun + 's'} yet.`}</p>
    <a class="btn primary" href="/people/new">Add a person</a>
  </div>
{:else}
  <ul class="card list" class:stale={loading}>
    {#each result.items as person (person.id)}
      <PersonRow {person} />
    {/each}
  </ul>
  <Pagination page={pageNumber} size={PAGE_SIZE} total={result.totalCount} disabled={loading} onchange={(p) => navigate(query, role, p)} />
{/if}

<style>
  .filters { display: flex; gap: 0.35rem; }
  .filters [aria-pressed='true'] { background: var(--text); color: var(--bg); border-color: var(--text); }
  .list { list-style: none; padding: 0; margin: 0; }
  .stale { opacity: 0.6; }
</style>
