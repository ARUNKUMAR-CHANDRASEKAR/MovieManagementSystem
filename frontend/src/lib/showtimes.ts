// Types and helpers for "Now showing" and seat booking.

export type Country = 'SINGAPORE' | 'MALAYSIA';

export const COUNTRIES: Record<Country, { label: string; timeZone: string }> = {
  SINGAPORE: { label: 'Singapore', timeZone: 'Asia/Singapore' },
  MALAYSIA: { label: 'Malaysia', timeZone: 'Asia/Kuala_Lumpur' }
};

export const COUNTRY_LIST = Object.keys(COUNTRIES) as Country[];

/** Same limit as showtime-service. */
export const MAX_SEATS = 8;

export interface Theatre {
  id: string;
  name: string;
  country: Country;
  location: string;
  seatRows: number;
  seatsPerRow: number;
}

export interface Showtime {
  id: string;
  /** UTC instant, e.g. 2026-10-12T11:30:00Z */
  startsAt: string;
  priceCents: number;
  currency: string;
  seatsLeft: number;
  soldSeats: string[];
  theatre: Theatre;
  movie?: { id: string; title: string; artworkUrl: string | null } | null;
}

export interface Booking {
  reference: string;
  seats: string[];
  totalCents: number;
  currency: string;
  customerName: string;
  showtime: Showtime;
}

/** Reads a country from the URL; anything else falls back to Singapore. */
export function parseCountry(value: string | null): Country {
  return value === 'MALAYSIA' ? 'MALAYSIA' : 'SINGAPORE';
}

/** "SGD 13.50". Cents are whole numbers, so there is no floating-point rounding surprise. */
export function formatMoney(cents: number, currency: string): string {
  const abs = Math.abs(cents);
  const sign = cents < 0 ? '-' : '';
  return `${currency} ${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`;
}

/** Times are shown in the theatre's time zone, not the viewer's. */
export function formatTime(iso: string, country: Country): string {
  return new Intl.DateTimeFormat('en-SG', { hour: 'numeric', minute: '2-digit', timeZone: COUNTRIES[country].timeZone }).format(
    new Date(iso)
  );
}

export function formatDay(iso: string, country: Country): string {
  return new Intl.DateTimeFormat('en-SG', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    timeZone: COUNTRIES[country].timeZone
  }).format(new Date(iso));
}

/** YYYY-MM-DD in the theatre's time zone, used to group shows by day. */
export function dayKey(iso: string, country: Country): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: COUNTRIES[country].timeZone }).format(new Date(iso));
}

export interface TheatreGroup {
  theatre: Theatre;
  days: { key: string; label: string; shows: Showtime[] }[];
}

/** Groups shows (already sorted by location then time) into theatres, then days. */
export function groupShowtimes(shows: Showtime[]): TheatreGroup[] {
  const map = new Map<string, TheatreGroup>();
  for (const show of shows) {
    let group = map.get(show.theatre.id);
    if (!group) {
      group = { theatre: show.theatre, days: [] };
      map.set(show.theatre.id, group);
    }
    const key = dayKey(show.startsAt, show.theatre.country);
    let day = group.days.find((d) => d.key === key);
    if (!day) group.days.push((day = { key, label: formatDay(show.startsAt, show.theatre.country), shows: [] }));
    day.shows.push(show);
  }
  return Array.from(map.values());
}

/** Seat labels row by row: [["A1".."A12"], ["B1"..], …]. Row A is nearest the screen. */
export function seatGrid(rows: number, seatsPerRow: number): string[][] {
  return Array.from({ length: rows }, (_, r) =>
    Array.from({ length: seatsPerRow }, (_, n) => `${String.fromCharCode(65 + r)}${n + 1}`)
  );
}

/** Front row first, then by number: A2 before A10. */
export function sortSeats(seats: string[]): string[] {
  return [...seats].sort((a, b) => a.localeCompare(b, 'en', { numeric: true }));
}

/** Selects or unselects a seat. Returns an error instead when the seat is sold or the limit is reached. */
export function toggleSeat(
  selected: string[],
  seat: string,
  sold: string[],
  max = MAX_SEATS
): { selected: string[]; error?: string } {
  if (sold.includes(seat)) return { selected, error: `Seat ${seat} is already sold` };
  if (selected.includes(seat)) return { selected: selected.filter((s) => s !== seat) };
  if (selected.length >= max) return { selected, error: `You can book at most ${max} seats at a time` };
  return { selected: sortSeats([...selected, seat]) };
}
