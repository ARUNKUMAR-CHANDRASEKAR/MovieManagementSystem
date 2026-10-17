<script lang="ts">
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { ApiError, gql } from '$lib/api';
  import { ADD_CREDIT, DELETE_MOVIE, MOVIE, REMOVE_CREDIT, UPDATE_MOVIE } from '$lib/queries';
  import { type Movie, type MovieInput, type Person, type Role } from '$lib/types';
  import { notify } from '$lib/toast.svelte';
  import ArtworkUpload from '$lib/components/ArtworkUpload.svelte';
  import MovieForm from '$lib/components/MovieForm.svelte';
  import ConfirmDelete from '$lib/components/ConfirmDelete.svelte';
  import PersonPicker from '$lib/components/PersonPicker.svelte';
  import PersonRow from '$lib/components/PersonRow.svelte';

  const id = $derived(page.params.id!);
  let movie = $state<Movie | null>(null);
  let error = $state<ApiError | null>(null);
  let editing = $state(false);
  let removingId = $state<string | null>(null);
  let reload = $state(0);
  // An unknown or malformed id (e.g. /movies/abc): retrying cannot help, so no "Try again" button.
  const missing = $derived(error?.type === 'NOT_FOUND' || error?.type === 'BAD_REQUEST');

  $effect(() => {
    const movieId = id;
    void reload;
    movie = null;
    error = null;
    gql<{ movie: Movie }>(MOVIE, { id: movieId })
      .then((data) => (movie = data.movie))
      .catch((e) => (error = e));
  });

  const people = $derived(movie?.people ?? []);
  const byRole = (role: Role) => people.filter((p) => p.role === role);

  async function save(input: MovieInput) {
    try {
      const data = await gql<{ updateMovie: Movie }>(UPDATE_MOVIE, { id, input });
      movie = data.updateMovie;
      editing = false;
      notify('success', 'Movie updated');
    } catch (e) {
      notify('error', (e as Error).message);
    }
  }

  async function remove() {
    if (!movie) return;
    try {
      await gql(DELETE_MOVIE, { id });
      notify('success', `"${movie.title}" deleted`);
      goto('/');
    } catch (e) {
      notify('error', (e as Error).message);
    }
  }

  async function addPerson(person: Person) {
    try {
      const data = await gql<{ addCredit: Movie }>(ADD_CREDIT, { movieId: id, personId: person.id });
      if (movie) movie.people = data.addCredit.people;
      notify('success', `${person.name} added`);
    } catch (e) {
      notify('error', (e as Error).message);
    }
  }

  async function removePerson(person: Person) {
    removingId = person.id;
    try {
      const data = await gql<{ removeCredit: Movie }>(REMOVE_CREDIT, { movieId: id, personId: person.id });
      if (movie) movie.people = data.removeCredit.people;
      notify('success', `${person.name} removed from this movie`);
    } catch (e) {
      notify('error', (e as Error).message);
    } finally {
      removingId = null;
    }
  }
</script>

<svelte:head><title>{movie?.title ?? 'Movie'} · Movie Manager</title></svelte:head>

<a href="/" class="hint">← Movies</a>

{#if error}
  <div class="alert" role="alert">
    <span>{missing ? 'This movie does not exist. It may have been deleted.' : error.message}</span>
    {#if !missing}<button class="btn small" onclick={() => reload++}>Try again</button>{/if}
  </div>
{:else if !movie}
  <div class="layout" aria-busy="true">
    <div class="skeleton poster"></div>
    <div style="display: grid; gap: 0.75rem; align-content: start">
      <div class="skeleton" style="height: 2rem; width: 60%"></div>
      <div class="skeleton" style="height: 1rem; width: 30%"></div>
      <div class="skeleton" style="height: 5rem"></div>
    </div>
  </div>
  <p class="status"><span class="spinner"></span> Loading movie…</p>
{:else}
  <div class="layout">
    <div class="artwork">
      <div class="poster card">
        {#if movie.artworkUrl}
          <img src={movie.artworkUrl} alt="Artwork for {movie.title}" />
        {:else}
          <span>No artwork yet</span>
        {/if}
      </div>
      <ArtworkUpload movieId={movie.id} hasArtwork={!!movie.artworkUrl} onuploaded={(url) => movie && (movie.artworkUrl = url)} />
    </div>

    <div class="details">
      {#if editing}
        <div class="card panel">
          <h2>Edit movie</h2>
          <MovieForm {movie} submitLabel="Save changes" onsubmit={save} oncancel={() => (editing = false)} />
        </div>
      {:else}
        <h1>{movie.title}</h1>
        <p class="hint">{[movie.releaseYear, movie.genre].filter(Boolean).join(' · ') || 'No year or genre'}</p>
        {#if movie.synopsis}<p class="synopsis">{movie.synopsis}</p>{/if}
        <div class="toolbar">
          <a class="btn primary" href="/movies/{movie.id}/showtimes">Book tickets</a>
          <button class="btn" onclick={() => (editing = true)}>Edit</button>
          <ConfirmDelete message={`Delete "${movie.title}"? This cannot be undone.`} ondelete={remove} />
        </div>
      {/if}

      <section>
        <h2>Cast &amp; creators</h2>
        {#each [['CREATOR', 'Creators'], ['CAST', 'Cast']] as const as [role, label] (role)}
          {@const list = byRole(role)}
          <h3 class="hint">{label} ({list.length})</h3>
          {#if list.length}
            <ul class="card list">
              {#each list as person (person.id)}
                <PersonRow {person}>
                  {#snippet actions()}
                    <button class="btn small danger" disabled={removingId === person.id} onclick={() => removePerson(person)}>
                      {removingId === person.id ? 'Removing…' : 'Remove'}
                    </button>
                  {/snippet}
                </PersonRow>
              {/each}
            </ul>
          {:else}
            <p class="hint">None added yet.</p>
          {/if}
        {/each}
        <div class="picker card panel">
          <PersonPicker excludeIds={people.map((p) => p.id)} onpick={addPerson} />
        </div>
      </section>
    </div>
  </div>
{/if}

<style>
  .layout { display: grid; gap: 1.5rem; grid-template-columns: minmax(0, 300px) minmax(0, 1fr); margin-top: 1rem; }
  @media (max-width: 700px) { .layout { grid-template-columns: 1fr; } .artwork { max-width: 300px; } }
  .artwork { display: grid; gap: 0.75rem; align-content: start; }
  .poster { aspect-ratio: 2 / 3; overflow: hidden; display: grid; place-items: center; color: var(--muted); }
  .poster img { width: 100%; height: 100%; object-fit: cover; display: block; }
  .synopsis { white-space: pre-line; }
  section { margin-top: 2rem; }
  h3 { font-size: 0.9rem; margin-top: 1rem; }
  .list { list-style: none; padding: 0; margin: 0 0 0.5rem; }
  .picker { margin-top: 1rem; }
</style>
