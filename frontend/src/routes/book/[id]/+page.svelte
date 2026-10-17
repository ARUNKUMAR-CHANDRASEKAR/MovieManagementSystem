<script lang="ts">
  import { page } from '$app/state';
  import { ApiError, gql } from '$lib/api';
  import { BOOK_SEATS, SHOWTIME } from '$lib/queries';
  import { COUNTRIES, MAX_SEATS, formatDay, formatMoney, formatTime, toggleSeat, type Booking, type Showtime } from '$lib/showtimes';
  import { notify } from '$lib/toast.svelte';
  import { queryString } from '$lib/utils';
  import SeatMap from '$lib/components/SeatMap.svelte';

  const id = $derived(page.params.id!);
  let show = $state<Showtime | null>(null);
  let error = $state<ApiError | null>(null);
  let selected = $state<string[]>([]);
  let name = $state('');
  let nameError = $state('');
  let bookingError = $state('');
  let booking = $state<Booking | null>(null);
  let submitting = $state(false); // request in progress
  let reload = $state(0);

  $effect(() => {
    const showId = id;
    void reload;
    error = null;
    gql<{ showtime: Showtime }>(SHOWTIME, { id: showId })
      .then((data) => {
        show = data.showtime;
        // Drop any selected seat that someone else bought in the meantime.
        const soldSet = new Set(data.showtime.soldSeats);
        selected = selected.filter((s) => !soldSet.has(s));
      })
      .catch((e) => (error = e));
  });

  const country = $derived(show?.theatre.country ?? 'SINGAPORE');
  const total = $derived((show?.priceCents ?? 0) * selected.length);

  function toggle(seat: string) {
    if (!show) return;
    const result = toggleSeat(selected, seat, show.soldSeats);
    selected = result.selected;
    bookingError = result.error ?? '';
  }

  async function book(event: SubmitEvent) {
    event.preventDefault();
    if (!show || !show.movie || selected.length === 0 || submitting) return; // submitting: ignore double-clicks
    nameError = name.trim() ? '' : 'Name is required';
    if (nameError) return;
    submitting = true;
    bookingError = '';
    try {
      const data = await gql<{ bookSeats: Booking }>(BOOK_SEATS, { showtimeId: show.id, seats: selected, customerName: name.trim() });
      booking = data.bookSeats;
      show = { ...show, ...data.bookSeats.showtime };
      selected = [];
      notify('success', `Booked! Reference ${booking.reference}`);
    } catch (e) {
      bookingError = (e as Error).message;
      reload++; // refresh the seat map: the usual cause is a seat sold a moment ago
    } finally {
      submitting = false;
    }
  }

  function bookMore() {
    booking = null;
  }
</script>

<svelte:head><title>{show?.movie?.title ? `Book ${show.movie.title}` : 'Book seats'} · Movie Manager</title></svelte:head>

{#if show?.movie}
  <a href="/movies/{show.movie.id}/showtimes{queryString({ country })}" class="hint">← All showtimes</a>
{:else}
  <a href="/showing" class="hint">← Now showing</a>
{/if}

{#if error}
  <div class="alert" role="alert">
    <span>{error.type === 'NOT_FOUND' || error.type === 'BAD_REQUEST' ? 'This show does not exist.' : error.message}</span>
    {#if error.type !== 'NOT_FOUND' && error.type !== 'BAD_REQUEST'}<button class="btn small" onclick={() => reload++}>Try again</button>{/if}
  </div>
{:else if !show}
  <p class="status"><span class="spinner"></span> Loading seat map…</p>
{:else}
  <header class="show">
    <h1>{show.movie?.title ?? 'Movie no longer available'}</h1>
    <p>
      <strong>{formatDay(show.startsAt, country)}, {formatTime(show.startsAt, country)}</strong>
      · {show.theatre.name}, {show.theatre.location}, {COUNTRIES[country].label}
      · {formatMoney(show.priceCents, show.currency)} per seat
    </p>
  </header>

  {#if !show.movie && !booking}
    <div class="alert" role="alert">
      <span>This movie has been removed, so this show can no longer be booked.</span>
    </div>
  {:else if booking}
    <section class="card panel confirmed" aria-live="polite">
      <p class="hint">Booking confirmed (sample: no payment taken)</p>
      <h2>{booking.reference}</h2>
      <dl>
        <dt>Name</dt><dd>{booking.customerName}</dd>
        <dt>Seats</dt><dd>{booking.seats.join(', ')}</dd>
        <dt>Total</dt><dd>{formatMoney(booking.totalCents, booking.currency)}</dd>
        <dt>Show</dt><dd>{formatDay(show.startsAt, country)}, {formatTime(show.startsAt, country)} at {show.theatre.name}</dd>
      </dl>
      <div class="toolbar">
        <button class="btn primary" onclick={bookMore}>Book more seats</button>
        {#if show.movie}<a class="btn" href="/movies/{show.movie.id}/showtimes{queryString({ country })}">Other showtimes</a>{/if}
      </div>
    </section>
  {:else}
    <div class="layout">
      <section class="card panel">
        <SeatMap
          rows={show.theatre.seatRows}
          seatsPerRow={show.theatre.seatsPerRow}
          sold={show.soldSeats}
          {selected}
          disabled={submitting}
          ontoggle={toggle}
        />
        <p class="hint center">{show.seatsLeft} of {show.theatre.seatRows * show.theatre.seatsPerRow} seats left · up to {MAX_SEATS} per booking</p>
      </section>

      <form class="card panel summary" onsubmit={book} novalidate>
        <h2>Your seats</h2>
        {#if selected.length === 0}
          <p class="hint">Tap seats on the map to choose them.</p>
        {:else}
          <p class="seats">{selected.join(', ')}</p>
          <p>{selected.length} × {formatMoney(show.priceCents, show.currency)}</p>
          <p class="total">Total <strong>{formatMoney(total, show.currency)}</strong></p>
        {/if}
        <div class="field">
          <label for="customer-name">Name for the booking *</label>
          <input id="customer-name" bind:value={name} maxlength="100" autocomplete="name" aria-invalid={!!nameError} />
          {#if nameError}<span class="field-error">{nameError}</span>{/if}
        </div>
        {#if bookingError}<p class="field-error" role="alert">{bookingError}</p>{/if}
        <button class="btn primary" type="submit" disabled={selected.length === 0 || submitting}>
          {#if submitting}<span class="spinner"></span> Booking…{:else}Proceed to book{selected.length ? ` · ${formatMoney(total, show.currency)}` : ''}{/if}
        </button>
        <p class="hint">Sample booking: no payment is taken.</p>
      </form>
    </div>
  {/if}
{/if}

<style>
  .show { margin: 1rem 0; }
  .show h1 { margin-bottom: 0.25rem; }
  .show p { margin: 0; color: var(--muted); }
  .layout { display: grid; gap: 1rem; grid-template-columns: minmax(0, 1fr) 300px; align-items: start; }
  @media (max-width: 800px) { .layout { grid-template-columns: 1fr; } }
  .center { text-align: center; margin: 0.75rem 0 0; }
  .summary h2 { font-size: 1.1rem; }
  .summary p { margin: 0 0 0.5rem; }
  .seats { font-weight: 600; }
  .total { font-size: 1.1rem; }
  .summary button { width: 100%; margin-bottom: 0.5rem; }
  .confirmed h2 { font-size: 2rem; letter-spacing: 0.05em; }
  dl { display: grid; grid-template-columns: auto 1fr; gap: 0.35rem 1rem; margin: 1rem 0; }
  dt { color: var(--muted); }
  dd { margin: 0; }
</style>
