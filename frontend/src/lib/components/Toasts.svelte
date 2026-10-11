<script lang="ts">
  import { dismiss, toasts } from '$lib/toast.svelte';
</script>

<div class="toasts" aria-live="polite">
  {#each toasts as toast (toast.id)}
    <div class="toast {toast.kind}" role={toast.kind === 'error' ? 'alert' : 'status'}>
      <span>{toast.message}</span>
      <button type="button" aria-label="Dismiss" onclick={() => dismiss(toast.id)}>×</button>
    </div>
  {/each}
</div>

<style>
  .toasts {
    position: fixed; right: 1rem; bottom: 1rem; left: 1rem; z-index: 10;
    display: grid; gap: 0.5rem; justify-items: end; pointer-events: none;
  }
  .toast {
    pointer-events: auto; display: flex; gap: 0.75rem; align-items: center;
    max-width: 420px; padding: 0.7rem 0.9rem; border-radius: 8px;
    background: var(--surface); border: 1px solid var(--border); border-left: 4px solid; box-shadow: var(--shadow);
  }
  .success { border-left-color: var(--success); }
  .error { border-left-color: var(--danger); }
  button { background: none; border: 0; color: var(--muted); font-size: 1.25rem; cursor: pointer; line-height: 1; }
</style>
