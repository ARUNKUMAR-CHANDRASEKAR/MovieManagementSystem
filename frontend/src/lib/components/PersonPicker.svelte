<script lang="ts">
  import { gql, isAbort } from '$lib/api';
  import { PEOPLE } from '$lib/queries';
  import { ROLE_LABEL, type Page, type Person } from '$lib/types';
  import { debounce } from '$lib/utils';

  interface Props {
    /** Ids already credited; shown as added. */
    excludeIds: string[];
    onpick: (person: Person) => Promise<void>;
  }
  let { excludeIds, onpick }: Props = $props();

  let search = $state('');
  let results = $state<Person[]>([]);
  let loading = $state(false);
  let error = $state('');
  let addingId = $state<string | null>(null);
  let controller: AbortController | undefined;

  const find = debounce(async (text: string) => {
    controller?.abort(); // a newer search replaces an older one still in flight
    if (!text.trim()) {
      results = [];
      loading = false;
      return;
    }
    controller = new AbortController();
    try {
      const data = await gql<{ people: Page<Person> }>(PEOPLE, { search: text, size: 8 }, controller.signal);
      results = data.people.items;
      error = '';
      loading = false;
    } catch (e) {
      if (isAbort(e)) return;
      error = (e as Error).message;
      loading = false;
    }
  }, 300);

  function oninput() {
    loading = !!search.trim();
    find(search);
  }

  async function pick(person: Person) {
    addingId = person.id;
    try {
      await onpick(person);
    } finally {
      addingId = null;
    }
  }
</script>

<div class="picker">
  <label for="person-search">Add cast or creator</label>
  <input id="person-search" type="search" placeholder="Search people by name…" bind:value={search} {oninput} autocomplete="off" />
  <div class="status">
    {#if loading}<span class="spinner"></span> Searching…{:else if error}<span class="field-error">{error}</span>{:else if search.trim() && results.length === 0}No people match "{search.trim()}". <a href="/people/new">Add a person</a>{/if}
  </div>
  {#if results.length}
    <ul class="card">
      {#each results as person (person.id)}
        {@const added = excludeIds.includes(person.id)}
        <li>
          <span>{person.name} <span class="badge {person.role}">{ROLE_LABEL[person.role]}</span></span>
          <button class="btn small" disabled={added || addingId !== null} onclick={() => pick(person)}>
            {added ? 'Added' : addingId === person.id ? 'Adding…' : 'Add'}
          </button>
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style>
  .picker { display: grid; gap: 0.35rem; }
  ul { list-style: none; margin: 0; padding: 0; }
  li { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border); }
  li:last-child { border-bottom: 0; }
</style>
