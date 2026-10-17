import { describe, expect, it } from 'vitest';
import {
  dayKey,
  formatDay,
  formatMoney,
  formatTime,
  groupShowtimes,
  parseCountry,
  seatGrid,
  sortSeats,
  toggleSeat,
  type Showtime,
  type Theatre
} from './showtimes';

const orchard: Theatre = { id: '1', name: 'Orchard Cineplex', country: 'SINGAPORE', location: 'Orchard', seatRows: 8, seatsPerRow: 12 };
const yishun: Theatre = { ...orchard, id: '2', name: 'Yishun Cineplex', location: 'Yishun' };
const show = (id: string, theatre: Theatre, startsAt: string): Showtime => ({
  id,
  startsAt,
  priceCents: 1350,
  currency: 'SGD',
  seatsLeft: 96,
  soldSeats: [],
  theatre
});

describe('formatMoney', () => {
  it.each([
    [1350, 'SGD 13.50'],
    [5, 'SGD 0.05'],
    [100, 'SGD 1.00'],
    [0, 'SGD 0.00'],
    [123456, 'SGD 1234.56']
  ])('%i cents -> %s', (cents, text) => {
    expect(formatMoney(cents, 'SGD')).toBe(text);
  });
});

describe('time zone handling', () => {
  // 15:30 UTC is 23:30 the same day in Singapore, and 16:30 UTC is already the next day there.
  it('shows the time in the theatre time zone', () => {
    expect(formatTime('2026-10-12T11:30:00Z', 'SINGAPORE').toLowerCase()).toBe('7:30 pm');
    expect(formatTime('2026-10-12T11:30:00Z', 'MALAYSIA').toLowerCase()).toBe('7:30 pm');
  });

  it('groups by the local day, not the UTC day', () => {
    expect(dayKey('2026-10-12T15:30:00Z', 'SINGAPORE')).toBe('2026-10-12');
    expect(dayKey('2026-10-12T16:30:00Z', 'SINGAPORE')).toBe('2026-10-13');
    expect(formatDay('2026-10-12T16:30:00Z', 'SINGAPORE')).toMatch(/Tue.*13.*Oct/);
  });
});

describe('groupShowtimes', () => {
  it('groups by theatre then local day, keeping order', () => {
    const groups = groupShowtimes([
      show('1', orchard, '2026-10-12T02:30:00Z'),
      show('2', orchard, '2026-10-12T11:30:00Z'),
      show('3', orchard, '2026-10-13T02:30:00Z'),
      show('4', yishun, '2026-10-12T06:00:00Z')
    ]);
    expect(groups.map((g) => g.theatre.location)).toEqual(['Orchard', 'Yishun']);
    expect(groups[0].days.map((d) => d.key)).toEqual(['2026-10-12', '2026-10-13']);
    expect(groups[0].days[0].shows.map((s) => s.id)).toEqual(['1', '2']);
  });

  it('returns nothing for no shows', () => {
    expect(groupShowtimes([])).toEqual([]);
  });
});

describe('seatGrid', () => {
  it('labels rows A, B… and seats from 1', () => {
    const grid = seatGrid(8, 12);
    expect(grid).toHaveLength(8);
    expect(grid[0][0]).toBe('A1');
    expect(grid[7][11]).toBe('H12');
  });
});

describe('sortSeats', () => {
  it('sorts numbers as numbers', () => {
    expect(sortSeats(['B1', 'A10', 'A2'])).toEqual(['A2', 'A10', 'B1']);
  });
});

describe('toggleSeat', () => {
  it('selects and unselects', () => {
    const first = toggleSeat([], 'C5', []);
    expect(first.selected).toEqual(['C5']);
    expect(toggleSeat(first.selected, 'C5', []).selected).toEqual([]);
  });

  it('keeps the selection sorted', () => {
    expect(toggleSeat(['C5'], 'A10', []).selected).toEqual(['A10', 'C5']);
  });

  it('refuses sold seats', () => {
    expect(toggleSeat([], 'A1', ['A1'])).toEqual({ selected: [], error: 'Seat A1 is already sold' });
  });

  it('allows 8 seats and refuses a 9th, but still allows unselecting', () => {
    const eight = ['A1', 'A2', 'A3', 'A4', 'A5', 'A6', 'A7', 'A8'];
    expect(toggleSeat(eight, 'A9', []).error).toBe('You can book at most 8 seats at a time');
    expect(toggleSeat(eight, 'A8', []).selected).toHaveLength(7);
  });
});

describe('parseCountry', () => {
  it.each([
    ['MALAYSIA', 'MALAYSIA'],
    ['SINGAPORE', 'SINGAPORE'],
    [null, 'SINGAPORE'],
    ['INDIA', 'SINGAPORE']
  ])('%j -> %s', (value, country) => {
    expect(parseCountry(value)).toBe(country);
  });
});
