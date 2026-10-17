<script lang="ts">
  import { untrack } from 'svelte';
  import type { Movie, MovieInput } from '$lib/types';
  import { LIMITS, validateMovie, type FieldErrors, type MovieFormValues } from '$lib/validation';

  interface Props {
    movie?: Movie;
    submitLabel: string;
    onsubmit: (input: MovieInput) => Promise<void>;
    oncancel?: () => void;
  }
  let { movie, submitLabel, onsubmit, oncancel }: Props = $props();

  // Copy the starting values once (untrack); the form then owns its state.
  const start = untrack(() => movie);
  let values = $state<MovieFormValues>({
    title: start?.title ?? '',
    releaseYear: start?.releaseYear?.toString() ?? '',
    genre: start?.genre ?? '',
    synopsis: start?.synopsis ?? ''
  });
  let errors = $state<FieldErrors>({});
  let saving = $state(false);

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    // A fast double-click fires submit twice before the disabled button re-renders.
    if (saving) return;
    const result = validateMovie(values);
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
    <label for="title">Title *</label>
    <input id="title" bind:value={values.title} maxlength={LIMITS.title} aria-invalid={!!errors.title} required />
    {#if errors.title}<span class="field-error">{errors.title}</span>{/if}
  </div>
  <div class="two">
    <div class="field">
      <label for="year">Release year</label>
      <input id="year" bind:value={values.releaseYear} inputmode="numeric" placeholder="e.g. 2023" aria-invalid={!!errors.releaseYear} />
      {#if errors.releaseYear}<span class="field-error">{errors.releaseYear}</span>{/if}
    </div>
    <div class="field">
      <label for="genre">Genre</label>
      <input id="genre" bind:value={values.genre} maxlength={LIMITS.genre} placeholder="e.g. Action" aria-invalid={!!errors.genre} />
      {#if errors.genre}<span class="field-error">{errors.genre}</span>{/if}
    </div>
  </div>
  <div class="field">
    <label for="synopsis">Synopsis</label>
    <textarea id="synopsis" bind:value={values.synopsis} maxlength={LIMITS.synopsis} aria-invalid={!!errors.synopsis}></textarea>
    <span class="hint">{values.synopsis.length} / {LIMITS.synopsis}</span>
    {#if errors.synopsis}<span class="field-error">{errors.synopsis}</span>{/if}
  </div>
  <div class="actions">
    <button class="btn primary" type="submit" disabled={saving}>
      {#if saving}<span class="spinner"></span> Saving…{:else}{submitLabel}{/if}
    </button>
    {#if oncancel}<button class="btn" type="button" onclick={oncancel} disabled={saving}>Cancel</button>{/if}
  </div>
</form>

<style>
  .two { display: grid; gap: 0 1rem; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); }
  .actions { display: flex; gap: 0.75rem; flex-wrap: wrap; }
</style>
