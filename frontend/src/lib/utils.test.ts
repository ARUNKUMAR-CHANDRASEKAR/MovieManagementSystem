import { afterEach, describe, expect, it, vi } from 'vitest';
import { debounce, parsePage, plural, queryString } from './utils';

describe('debounce', () => {
  afterEach(() => vi.useRealTimers());

  it('calls once with the last arguments after the delay', () => {
    vi.useFakeTimers();
    const fn = vi.fn();
    const debounced = debounce(fn, 300);
    debounced('a');
    debounced('ab');
    vi.advanceTimersByTime(299);
    expect(fn).not.toHaveBeenCalled();
    debounced('abc');
    vi.advanceTimersByTime(300);
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith('abc');
  });
});

describe('queryString', () => {
  it('leaves out empty values and page 0', () => {
    expect(queryString({ q: '', role: null, page: 0 })).toBe('?');
  });

  it('encodes values', () => {
    expect(queryString({ q: 'tom & jerry', page: 2 })).toBe('?q=tom+%26+jerry&page=2');
  });
});

describe('parsePage', () => {
  it.each([
    [null, 0],
    ['', 0],
    ['3', 3],
    ['-1', 0],
    ['1.5', 0],
    ['abc', 0]
  ])('%j -> %i', (value, page) => {
    expect(parsePage(value)).toBe(page);
  });
});

describe('plural', () => {
  it('handles 0, 1 and many', () => {
    expect(plural(0, 'movie')).toBe('0 movies');
    expect(plural(1, 'movie')).toBe('1 movie');
    expect(plural(2, 'movie')).toBe('2 movies');
  });
});
