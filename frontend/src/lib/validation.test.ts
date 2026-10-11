import { describe, expect, it } from 'vitest';
import { formatBytes, LIMITS, validateArtwork, validateMovie, validatePerson } from './validation';

const now = new Date('2026-06-01T00:00:00Z');
const movie = (overrides = {}) => ({
  title: 'Vikram',
  releaseYear: '2022',
  genre: 'Action',
  synopsis: '',
  region: '' as const,
  ...overrides
});

describe('validateMovie', () => {
  it('returns trimmed input when valid', () => {
    const { input, errors } = validateMovie(movie({ title: '  Leo  ', genre: ' Thriller ', synopsis: ' x ' }), now);
    expect(errors).toEqual({});
    expect(input).toEqual({ title: 'Leo', releaseYear: 2022, genre: 'Thriller', synopsis: 'x', region: null });
  });

  it('passes the chosen region through', () => {
    expect(validateMovie({ ...movie(), region: 'SINGAPORE' }, now).input?.region).toBe('SINGAPORE');
  });

  it('treats an empty year as no year', () => {
    expect(validateMovie(movie({ releaseYear: '  ' }), now).input?.releaseYear).toBeNull();
  });

  it.each(['', '   ', '\n'])('requires a title (%j)', (title) => {
    const { input, errors } = validateMovie(movie({ title }), now);
    expect(input).toBeUndefined();
    expect(errors.title).toBe('Title is required');
  });

  it('accepts a 200 character title and rejects 201', () => {
    expect(validateMovie(movie({ title: 'a'.repeat(200) }), now).errors.title).toBeUndefined();
    expect(validateMovie(movie({ title: 'a'.repeat(201) }), now).errors.title).toMatch(/200 characters/);
  });

  it.each(['1888', '2026', '2036'])('accepts year %s', (releaseYear) => {
    expect(validateMovie(movie({ releaseYear }), now).errors).toEqual({});
  });

  it.each(['1887', '2037', '0', '-1', '20.5', 'abc', '2e3', '12345'])('rejects year %s', (releaseYear) => {
    expect(validateMovie(movie({ releaseYear }), now).errors.releaseYear).toBe(
      'Release year must be between 1888 and 2036'
    );
  });

  it('checks genre and synopsis length', () => {
    const { errors } = validateMovie(movie({ genre: 'g'.repeat(101), synopsis: 's'.repeat(5001) }), now);
    expect(errors.genre).toBeDefined();
    expect(errors.synopsis).toBeDefined();
  });

  it('reports every invalid field at once', () => {
    const { errors } = validateMovie(movie({ title: '', releaseYear: '1' }), now);
    expect(Object.keys(errors).sort()).toEqual(['releaseYear', 'title']);
  });
});

describe('validatePerson', () => {
  it('returns trimmed input when valid', () => {
    expect(validatePerson({ name: ' Kamal ', role: 'CAST', bio: ' Actor ' }).input).toEqual({
      name: 'Kamal',
      role: 'CAST',
      bio: 'Actor'
    });
  });

  it('requires a name and a role', () => {
    const { input, errors } = validatePerson({ name: ' ', role: '', bio: '' });
    expect(input).toBeUndefined();
    expect(errors).toEqual({ name: 'Name is required', role: 'Choose cast or creator' });
  });

  it('accepts a 150 character name and rejects 151', () => {
    expect(validatePerson({ name: 'n'.repeat(150), role: 'CREATOR', bio: '' }).errors).toEqual({});
    expect(validatePerson({ name: 'n'.repeat(151), role: 'CREATOR', bio: '' }).errors.name).toBeDefined();
  });

  it('rejects a bio over 5000 characters', () => {
    expect(validatePerson({ name: 'A', role: 'CAST', bio: 'b'.repeat(5001) }).errors.bio).toBeDefined();
  });
});

describe('validateArtwork', () => {
  it.each(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])('accepts %s', (type) => {
    expect(validateArtwork({ type, size: 1000 })).toBeNull();
  });

  it.each(['image/svg+xml', 'text/html', 'application/pdf', ''])('rejects %j', (type) => {
    expect(validateArtwork({ type, size: 1000 })).toBe('Choose a JPEG, PNG, GIF or WebP image');
  });

  it('rejects an empty file', () => {
    expect(validateArtwork({ type: 'image/png', size: 0 })).toBe('The file is empty');
  });

  it('accepts exactly 5 MB and rejects one byte more', () => {
    expect(validateArtwork({ type: 'image/png', size: LIMITS.artworkBytes })).toBeNull();
    expect(validateArtwork({ type: 'image/png', size: LIMITS.artworkBytes + 1 })).toMatch(/limit is 5 MB/);
  });
});

describe('formatBytes', () => {
  it.each([
    [0, '0 B'],
    [1023, '1023 B'],
    [1024, '1.0 KB'],
    [1536, '1.5 KB'],
    [5 * 1024 * 1024, '5.0 MB']
  ])('%i -> %s', (bytes, text) => {
    expect(formatBytes(bytes)).toBe(text);
  });
});
