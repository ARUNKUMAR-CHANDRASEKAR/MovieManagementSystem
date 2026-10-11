<script lang="ts">
  import { fileToBase64, gqlWithProgress } from '$lib/api';
  import { UPLOAD_ARTWORK } from '$lib/queries';
  import { notify } from '$lib/toast.svelte';
  import { ARTWORK_TYPES, formatBytes, validateArtwork } from '$lib/validation';

  interface Props {
    movieId: string;
    hasArtwork: boolean;
    onuploaded: (artworkUrl: string) => void;
  }
  let { movieId, hasArtwork, onuploaded }: Props = $props();

  type Phase = 'idle' | 'reading' | 'uploading' | 'processing';
  let phase = $state<Phase>('idle');
  let percent = $state(0);
  let file = $state<File | null>(null);
  let error = $state('');
  let input: HTMLInputElement;

  const busy = $derived(phase !== 'idle');

  function choose(event: Event) {
    const picked = (event.currentTarget as HTMLInputElement).files?.[0] ?? null;
    error = picked ? (validateArtwork(picked) ?? '') : '';
    file = picked && !error ? picked : null;
  }

  async function upload() {
    if (!file) return;
    error = '';
    percent = 0;
    try {
      phase = 'reading';
      const dataBase64 = await fileToBase64(file);
      phase = 'uploading';
      const data = await gqlWithProgress<{ uploadArtwork: { artworkUrl: string } }>(
        UPLOAD_ARTWORK,
        { movieId, dataBase64 },
        (fraction) => {
          percent = Math.round(fraction * 100);
          // Every byte is sent; now the server is checking and saving the image.
          if (fraction >= 1) phase = 'processing';
        }
      );
      notify('success', 'Artwork uploaded');
      onuploaded(data.uploadArtwork.artworkUrl);
      file = null;
      input.value = '';
    } catch (e) {
      error = (e as Error).message;
    } finally {
      phase = 'idle';
    }
  }
</script>

<div class="upload">
  <label for="artwork-file" class="btn small">{hasArtwork ? 'Replace artwork' : 'Choose artwork'}</label>
  <input
    id="artwork-file"
    class="visually-hidden"
    type="file"
    accept={ARTWORK_TYPES.join(',')}
    bind:this={input}
    onchange={choose}
    disabled={busy}
  />
  <span class="hint">JPEG, PNG, GIF or WebP, up to 5 MB.</span>

  {#if file}
    <div class="chosen">
      <span>{file.name} ({formatBytes(file.size)})</span>
      <button class="btn primary small" onclick={upload} disabled={busy}>Upload</button>
    </div>
  {/if}

  {#if busy}
    <div class="progress" aria-live="polite">
      <progress max="100" value={phase === 'uploading' || phase === 'processing' ? percent : undefined}></progress>
      <span class="status">
        {#if phase === 'reading'}Reading file…{:else if phase === 'uploading'}Uploading… {percent}%{:else}<span class="spinner"></span> Saving artwork…{/if}
      </span>
    </div>
  {/if}

  {#if error}<p class="field-error" role="alert">{error}</p>{/if}
</div>

<style>
  .upload { display: grid; gap: 0.5rem; justify-items: start; }
  .chosen { display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap; font-size: 0.9rem; }
  .progress { width: 100%; display: grid; gap: 0.25rem; }
  progress { width: 100%; height: 0.6rem; accent-color: var(--accent); }
  p { margin: 0; }
</style>
