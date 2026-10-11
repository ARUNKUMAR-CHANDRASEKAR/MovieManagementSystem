<script lang="ts">
  let { count = 8 }: { count?: number } = $props();
</script>

<div class="movie-grid" aria-hidden="true">
  {#each Array(count) as _, i (i)}
    <div class="card">
      <div class="skeleton" style="aspect-ratio: 2 / 3; border-radius: var(--radius) var(--radius) 0 0"></div>
      <div style="padding: 0.75rem; display: grid; gap: 0.4rem">
        <div class="skeleton" style="height: 1rem; width: 80%"></div>
        <div class="skeleton" style="height: 0.8rem; width: 50%"></div>
      </div>
    </div>
  {/each}
</div>
