<script lang="ts">
  import { goto } from '$app/navigation';
  import { gql } from '$lib/api';
  import { CREATE_MOVIE } from '$lib/queries';
  import type { MovieInput } from '$lib/types';
  import { notify } from '$lib/toast.svelte';
  import MovieForm from '$lib/components/MovieForm.svelte';

  async function create(input: MovieInput) {
    try {
      const data = await gql<{ createMovie: { id: string } }>(CREATE_MOVIE, { input });
      notify('success', `"${input.title}" added. You can upload artwork now.`);
      goto(`/movies/${data.createMovie.id}`);
    } catch (e) {
      notify('error', (e as Error).message);
    }
  }
</script>

<svelte:head><title>Add movie · Movie Manager</title></svelte:head>

<a href="/" class="hint">← Movies</a>
<h1>Add movie</h1>
<div class="card panel form">
  <MovieForm submitLabel="Create movie" onsubmit={create} oncancel={() => history.back()} />
</div>

<style>
  .form { max-width: 640px; }
</style>
