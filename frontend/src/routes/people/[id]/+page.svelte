<script lang="ts">
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { ApiError, gql } from '$lib/api';
  import { DELETE_PERSON, PERSON, UPDATE_PERSON } from '$lib/queries';
  import { ROLE_LABEL, type Person, type PersonInput } from '$lib/types';
  import { notify } from '$lib/toast.svelte';
  import { plural } from '$lib/utils';
  import MovieCard from '$lib/components/MovieCard.svelte';
  import PersonForm from '$lib/components/PersonForm.svelte';
  import ConfirmDelete from '$lib/components/ConfirmDelete.svelte';

  const id = $derived(page.params.id!);
  let person = $state<Person | null>(null);
  let error = $state<ApiError | null>(null);
  let editing = $state(false);
  let reload = $state(0);

  $effect(() => {
    const personId = id;
    void reload;
    person = null;
    error = null;
    gql<{ person: Person }>(PERSON, { id: personId })
      .then((data) => (person = data.person))
      .catch((e) => (error = e));
  });

  async function save(input: PersonInput) {
    try {
      const data = await gql<{ updatePerson: Person }>(UPDATE_PERSON, { id, input });
      person = { ...data.updatePerson, movies: person?.movies };
      editing = false;
      notify('success', 'Person updated');
    } catch (e) {
      notify('error', (e as Error).message);
    }
  }

  const deleteMessage = $derived.by(() => {
    if (!person) return '';
    const count = person.movies?.length ?? 0;
    const warning = count ? ` They will also be removed from ${plural(count, 'movie')}.` : '';
    return `Delete ${person.name}?${warning} This cannot be undone.`;
  });

  async function remove() {
    if (!person) return;
    try {
      await gql(DELETE_PERSON, { id });
      notify('success', `${person.name} deleted`);
      goto('/people');
    } catch (e) {
      notify('error', (e as Error).message);
    }
  }
</script>

<svelte:head><title>{person?.name ?? 'Person'} · Movie Manager</title></svelte:head>

<a href="/people" class="hint">← Cast &amp; Creators</a>

{#if error}
  <div class="alert" role="alert">
    <span>{error.type === 'NOT_FOUND' ? 'This person does not exist. They may have been deleted.' : error.message}</span>
    {#if error.type !== 'NOT_FOUND'}<button class="btn small" onclick={() => reload++}>Try again</button>{/if}
  </div>
{:else if !person}
  <div style="display: grid; gap: 0.75rem; margin-top: 1rem" aria-busy="true">
    <div class="skeleton" style="height: 2rem; width: 40%"></div>
    <div class="skeleton" style="height: 4rem"></div>
  </div>
  <p class="status"><span class="spinner"></span> Loading…</p>
{:else}
  {#if editing}
    <div class="card panel form">
      <h2>Edit person</h2>
      <PersonForm {person} submitLabel="Save changes" onsubmit={save} oncancel={() => (editing = false)} />
    </div>
  {:else}
    <div class="head">
      <h1>{person.name}</h1>
      <span class="badge {person.role}">{ROLE_LABEL[person.role]}</span>
    </div>
    {#if person.bio}<p class="bio">{person.bio}</p>{/if}
    <div class="toolbar">
      <button class="btn" onclick={() => (editing = true)}>Edit</button>
      <ConfirmDelete message={deleteMessage} ondelete={remove} />
    </div>
  {/if}

  <h2>Movies ({person.movies?.length ?? 0})</h2>
  {#if person.movies?.length}
    <div class="movie-grid">
      {#each person.movies as movie (movie.id)}
        <MovieCard {movie} />
      {/each}
    </div>
  {:else}
    <p class="hint">Not credited on any movie yet. Open a movie to add them.</p>
  {/if}
{/if}

<style>
  .head { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; margin-top: 1rem; }
  .head h1 { margin: 0; }
  .bio { white-space: pre-line; max-width: 70ch; }
  .form { max-width: 640px; margin: 1rem 0; }
  h2 { margin-top: 2rem; }
</style>
