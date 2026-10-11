<script lang="ts">
  interface Props {
    page: number;
    size: number;
    total: number;
    disabled?: boolean;
    onchange: (page: number) => void;
  }
  let { page, size, total, disabled = false, onchange }: Props = $props();
  const pages = $derived(Math.max(1, Math.ceil(total / size)));
</script>

{#if pages > 1}
  <nav class="pagination" aria-label="Pagination">
    <button class="btn small" disabled={disabled || page <= 0} onclick={() => onchange(page - 1)}>← Previous</button>
    <span>Page {page + 1} of {pages}</span>
    <button class="btn small" disabled={disabled || page >= pages - 1} onclick={() => onchange(page + 1)}>Next →</button>
  </nav>
{/if}

<style>
  .pagination { display: flex; gap: 1rem; align-items: center; justify-content: center; margin: 1.5rem 0; color: var(--muted); }
</style>
