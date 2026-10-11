<script lang="ts">
  import '../app.css';
  import type { Snippet } from 'svelte';
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import Toasts from '$lib/components/Toasts.svelte';
  import { queryString } from '$lib/utils';

  let { children }: { children: Snippet } = $props();
  let search = $state('');

  function isActive(path: string) {
    return path === '/' ? page.url.pathname === '/' || page.url.pathname.startsWith('/movies') : page.url.pathname.startsWith(path);
  }

  function searchAll(event: SubmitEvent) {
    event.preventDefault();
    goto('/search' + queryString({ q: search.trim() }));
  }

  // Clearing the box (the × button, or deleting the text) on the search page goes back home.
  function clearToHome() {
    if (!search.trim() && page.url.pathname === '/search') goto('/');
  }
</script>

<header>
  <div class="container bar">
    <a href="/" class="logo">🎬 <span>Movie Manager</span></a>
    <nav>
      <a href="/" aria-current={isActive('/') ? 'page' : undefined}>Movies</a>
      <a href="/people" aria-current={isActive('/people') ? 'page' : undefined}>Cast &amp; Creators</a>
    </nav>
    <form role="search" onsubmit={searchAll}>
      <label for="global-search" class="visually-hidden">Search movies, cast and creators</label>
      <input id="global-search" type="search" placeholder="Search everything…" bind:value={search} oninput={clearToHome} />
    </form>
  </div>
</header>

<main class="container">
  {@render children()}
</main>

<Toasts />

<style>
  header { background: var(--surface); border-bottom: 1px solid var(--border); position: sticky; top: 0; z-index: 5; }
  .bar { display: flex; align-items: center; gap: 1rem 1.5rem; flex-wrap: wrap; padding-top: 0.6rem; padding-bottom: 0.6rem; }
  .logo { font-weight: 700; font-size: 1.1rem; text-decoration: none; white-space: nowrap; }
  nav { display: flex; gap: 0.25rem; }
  nav a { text-decoration: none; padding: 0.4rem 0.7rem; border-radius: 6px; color: var(--muted); font-weight: 500; white-space: nowrap; }
  nav a[aria-current='page'] { color: var(--text); background: var(--bg); }
  form { flex: 1 1 200px; margin-left: auto; max-width: 320px; }
  @media (max-width: 600px) {
    .logo span { display: none; }
    form { max-width: none; flex-basis: 100%; }
  }
</style>
