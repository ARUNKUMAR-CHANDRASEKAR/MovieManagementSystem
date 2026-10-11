<script lang="ts">
  import { REGION_LABEL, type Movie } from '$lib/types';
  let { movie }: { movie: Movie } = $props();
</script>

<a class="card movie" href="/movies/{movie.id}">
  <div class="poster">
    {#if movie.artworkUrl}
      <img src={movie.artworkUrl} alt="Artwork for {movie.title}" loading="lazy" />
    {:else}
      <span aria-hidden="true">{movie.title.slice(0, 1).toUpperCase()}</span>
    {/if}
  </div>
  <div class="info">
    <strong>{movie.title}</strong>
    <span class="hint">{[movie.releaseYear, movie.region && REGION_LABEL[movie.region], movie.genre].filter(Boolean).join(' · ') || ' '}</span>
  </div>
</a>

<style>
  .movie { display: flex; flex-direction: column; overflow: hidden; text-decoration: none; transition: transform 0.15s; }
  .movie:hover { transform: translateY(-2px); }
  .poster {
    aspect-ratio: 2 / 3; background: var(--skeleton);
    display: grid; place-items: center; font-size: 3rem; font-weight: 700; color: var(--muted);
  }
  img { width: 100%; height: 100%; object-fit: cover; display: block; font-size: 0.8rem; }
  .info { padding: 0.6rem 0.75rem 0.75rem; display: grid; gap: 0.15rem; }
  strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
