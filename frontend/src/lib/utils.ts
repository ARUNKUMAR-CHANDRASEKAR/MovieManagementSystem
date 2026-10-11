/** Calls fn only after it has not been called for `ms` milliseconds (used for search-as-you-type). */
export function debounce<A extends unknown[]>(fn: (...args: A) => void, ms: number): (...args: A) => void {
  let timer: ReturnType<typeof setTimeout> | undefined;
  return (...args: A) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

/** Builds "?q=...&page=..." leaving out empty values, so URLs stay short and shareable. */
export function queryString(params: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '' && value !== 0) search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : '?';
}

/** Reads a non-negative page number from the URL; anything invalid becomes 0. */
export function parsePage(value: string | null): number {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : 0;
}

export function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? '' : 's'}`;
}
