<script lang="ts">
  import { page } from '$app/state';
</script>

<svelte:head><title>{page.status === 404 ? 'Page not found' : 'Error'} · Movie Manager</title></svelte:head>

<div class="empty card">
  {#if page.status === 404}
    <h1>Page not found</h1>
    <p>There is nothing at <code>{page.url.pathname}</code>.</p>
  {:else}
    <h1>Something went wrong</h1>
    <p>{page.error?.message}</p>
  {/if}
  <a class="btn primary" href="/">Go to movies</a>
</div>
