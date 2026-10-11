<script lang="ts">
  import { untrack } from 'svelte';
  import type { Person, PersonInput } from '$lib/types';
  import { LIMITS, validatePerson, type FieldErrors, type PersonFormValues } from '$lib/validation';

  interface Props {
    person?: Person;
    submitLabel: string;
    onsubmit: (input: PersonInput) => Promise<void>;
    oncancel?: () => void;
  }
  let { person, submitLabel, onsubmit, oncancel }: Props = $props();

  // Copy the starting values once (untrack); the form then owns its state.
  const start = untrack(() => person);
  let values = $state<PersonFormValues>({ name: start?.name ?? '', role: start?.role ?? '', bio: start?.bio ?? '' });
  let errors = $state<FieldErrors>({});
  let saving = $state(false);

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    const result = validatePerson(values);
    errors = result.errors;
    if (!result.input) return;
    saving = true;
    try {
      await onsubmit(result.input);
    } finally {
      saving = false;
    }
  }
</script>

<form onsubmit={submit} novalidate>
  <div class="field">
    <label for="name">Name *</label>
    <input id="name" bind:value={values.name} maxlength={LIMITS.name} aria-invalid={!!errors.name} required />
    {#if errors.name}<span class="field-error">{errors.name}</span>{/if}
  </div>
  <fieldset class="field">
    <legend>Role *</legend>
    <div class="roles">
      <label><input type="radio" bind:group={values.role} value="CAST" /> Cast</label>
      <label><input type="radio" bind:group={values.role} value="CREATOR" /> Creator</label>
    </div>
    {#if errors.role}<span class="field-error">{errors.role}</span>{/if}
  </fieldset>
  <div class="field">
    <label for="bio">Bio</label>
    <textarea id="bio" bind:value={values.bio} maxlength={LIMITS.bio} aria-invalid={!!errors.bio}></textarea>
    {#if errors.bio}<span class="field-error">{errors.bio}</span>{/if}
  </div>
  <div class="actions">
    <button class="btn primary" type="submit" disabled={saving}>
      {#if saving}<span class="spinner"></span> Saving…{:else}{submitLabel}{/if}
    </button>
    {#if oncancel}<button class="btn" type="button" onclick={oncancel} disabled={saving}>Cancel</button>{/if}
  </div>
</form>

<style>
  fieldset { border: 0; padding: 0; margin: 0 0 1rem; }
  legend { font-weight: 500; font-size: 0.9rem; margin-bottom: 0.3rem; }
  .roles { display: flex; gap: 1.5rem; }
  .roles label { display: flex; gap: 0.4rem; align-items: center; font-weight: 400; }
  .roles input { width: auto; }
  .actions { display: flex; gap: 0.75rem; flex-wrap: wrap; }
</style>
