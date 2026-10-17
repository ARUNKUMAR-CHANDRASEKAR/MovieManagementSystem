<script lang="ts">
  import { seatGrid } from '$lib/showtimes';

  interface Props {
    rows: number;
    seatsPerRow: number;
    sold: string[];
    selected: string[];
    disabled?: boolean;
    ontoggle: (seat: string) => void;
  }
  let { rows, seatsPerRow, sold, selected, disabled = false, ontoggle }: Props = $props();

  const grid = $derived(seatGrid(rows, seatsPerRow));
  const status = (seat: string) => (sold.includes(seat) ? 'sold' : selected.includes(seat) ? 'selected' : 'available');
</script>

<div class="map">
  <div class="screen" aria-hidden="true">SCREEN</div>
  <div class="rows" style="--seats: {seatsPerRow}">
    {#each grid as row, r (r)}
      <span class="row-label" aria-hidden="true">{String.fromCharCode(65 + r)}</span>
      {#each row as seat (seat)}
        {@const state = status(seat)}
        <button
          type="button"
          class="seat {state}"
          disabled={disabled || state === 'sold'}
          aria-pressed={state === 'selected'}
          aria-label="Seat {seat}, {state}"
          title="{seat} · {state}"
          onclick={() => ontoggle(seat)}
        >{seat.slice(1)}</button>
      {/each}
    {/each}
  </div>
  <ul class="legend">
    <li><span class="seat available"></span> Available</li>
    <li><span class="seat selected"></span> Selected</li>
    <li><span class="seat sold"></span> Sold</li>
  </ul>
</div>

<style>
  .map { display: grid; gap: 1rem; justify-items: center; }
  .screen {
    width: 80%; text-align: center; font-size: 0.7rem; letter-spacing: 0.3em; color: var(--muted);
    border-top: 4px solid var(--muted); border-radius: 50% 50% 0 0 / 12px 12px 0 0; padding-top: 0.4rem;
  }
  .rows {
    display: grid; gap: 4px; width: 100%; max-width: 560px;
    grid-template-columns: 1.2rem repeat(var(--seats), minmax(0, 1fr));
  }
  .row-label { align-self: center; font-size: 0.75rem; color: var(--muted); text-align: center; }
  .seat {
    aspect-ratio: 1; min-width: 0; padding: 0; border-radius: 6px 6px 3px 3px;
    font: inherit; font-size: clamp(0.55rem, 1.6vw, 0.75rem); cursor: pointer;
    border: 1px solid var(--seat-free-border); background: var(--seat-free); color: var(--text);
  }
  .seat.available:hover:not(:disabled) { border-color: var(--seat-selected); }
  .seat.selected { background: var(--seat-selected); border-color: var(--seat-selected); color: #fff; font-weight: 700; }
  .seat.sold { background: var(--seat-sold); border-color: var(--seat-sold); color: transparent; cursor: not-allowed; }
  .seat:disabled:not(.sold) { cursor: not-allowed; opacity: 0.7; }
  .legend { display: flex; gap: 1.25rem; list-style: none; padding: 0; margin: 0; font-size: 0.85rem; flex-wrap: wrap; justify-content: center; }
  .legend li { display: flex; align-items: center; gap: 0.4rem; }
  .legend .seat { display: inline-block; width: 1.1rem; cursor: default; }
</style>
