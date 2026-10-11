<script lang="ts">
  import type { Snippet } from 'svelte';
  import { ROLE_LABEL, type Person } from '$lib/types';
  let { person, actions }: { person: Person; actions?: Snippet } = $props();
</script>

<li class="row">
  <span class="avatar" aria-hidden="true">{person.name.slice(0, 1).toUpperCase()}</span>
  <a href="/people/{person.id}" class="name">{person.name}</a>
  <span class="badge {person.role}">{ROLE_LABEL[person.role]}</span>
  {#if actions}{@render actions()}{/if}
</li>

<style>
  .row { display: flex; align-items: center; gap: 0.75rem; padding: 0.65rem 0.9rem; border-bottom: 1px solid var(--border); }
  .row:last-child { border-bottom: 0; }
  .avatar {
    width: 2.25rem; height: 2.25rem; border-radius: 50%; flex: none;
    display: grid; place-items: center; background: var(--skeleton); font-weight: 700; color: var(--muted);
  }
  .name { flex: 1; min-width: 0; font-weight: 500; text-decoration: none; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .name:hover { text-decoration: underline; }
</style>
