<script lang="ts">
  // Two-step delete inside the page. We avoid window.confirm(): some embedded browsers block it
  // (it returns false at once), which made the Delete button silently do nothing.
  interface Props {
    message: string;
    ondelete: () => Promise<void>;
  }
  let { message, ondelete }: Props = $props();

  let asking = $state(false);
  let deleting = $state(false);

  async function confirmDelete() {
    if (deleting) return; // ignore a double-click while the first delete runs
    deleting = true;
    try {
      await ondelete();
    } finally {
      deleting = false;
      asking = false;
    }
  }
</script>

{#if asking}
  <div class="confirm" role="alertdialog" aria-labelledby="confirm-message">
    <p id="confirm-message">{message}</p>
    <div class="actions">
      <button class="btn danger" onclick={confirmDelete} disabled={deleting}>
        {#if deleting}<span class="spinner"></span> Deleting…{:else}Yes, delete{/if}
      </button>
      <!-- svelte-ignore a11y_autofocus -->
      <button class="btn" onclick={() => (asking = false)} disabled={deleting} autofocus>Cancel</button>
    </div>
  </div>
{:else}
  <button class="btn danger" onclick={() => (asking = true)}>Delete</button>
{/if}

<style>
  .confirm { flex-basis: 100%; padding: 0.9rem 1rem; border: 1px solid var(--danger); border-radius: 8px; }
  p { margin: 0 0 0.75rem; }
  .actions { display: flex; gap: 0.75rem; flex-wrap: wrap; }
</style>
