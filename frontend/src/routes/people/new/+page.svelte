<script lang="ts">
  import { goto } from '$app/navigation';
  import { gql } from '$lib/api';
  import { CREATE_PERSON } from '$lib/queries';
  import type { PersonInput } from '$lib/types';
  import { notify } from '$lib/toast.svelte';
  import PersonForm from '$lib/components/PersonForm.svelte';

  async function create(input: PersonInput) {
    try {
      const data = await gql<{ createPerson: { id: string } }>(CREATE_PERSON, { input });
      notify('success', `${input.name} added`);
      goto(`/people/${data.createPerson.id}`);
    } catch (e) {
      notify('error', (e as Error).message);
    }
  }
</script>

<svelte:head><title>Add person · Movie Manager</title></svelte:head>

<a href="/people" class="hint">← Cast &amp; Creators</a>
<h1>Add cast member or creator</h1>
<div class="card panel form">
  <PersonForm submitLabel="Create person" onsubmit={create} oncancel={() => history.back()} />
</div>

<style>
  .form { max-width: 640px; }
</style>
