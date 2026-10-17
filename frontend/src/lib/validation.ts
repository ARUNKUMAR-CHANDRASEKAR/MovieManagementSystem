import type { MovieInput, PersonInput, Role } from './types';

// Same limits as the backend services. The backend is the authority; these give instant feedback.
export const LIMITS = {
  title: 200,
  genre: 100,
  synopsis: 5000,
  name: 150,
  bio: 5000,
  artworkBytes: 5 * 1024 * 1024,
  firstYear: 1888,
  yearsAhead: 10
};

export const ARTWORK_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];

export type FieldErrors = Record<string, string>;

export interface MovieFormValues {
  title: string;
  releaseYear: string;
  genre: string;
  synopsis: string;
}

export interface PersonFormValues {
  name: string;
  role: Role | '';
  bio: string;
}

function tooLong(label: string, value: string, max: number): string | undefined {
  return value.length > max ? `${label} must be ${max} characters or fewer` : undefined;
}

/** Validates form values. Returns the cleaned input when there are no errors. */
export function validateMovie(
  values: MovieFormValues,
  now = new Date()
): { input?: MovieInput; errors: FieldErrors } {
  const title = values.title.trim();
  const genre = values.genre.trim();
  const synopsis = values.synopsis.trim();
  const yearText = values.releaseYear.trim();
  const latestYear = now.getFullYear() + LIMITS.yearsAhead;
  const errors: FieldErrors = {};

  if (!title) errors.title = 'Title is required';
  else if (tooLong('Title', title, LIMITS.title)) errors.title = tooLong('Title', title, LIMITS.title)!;

  let releaseYear: number | null = null;
  if (yearText) {
    releaseYear = /^\d{1,4}$/.test(yearText) ? Number(yearText) : NaN;
    if (!(releaseYear >= LIMITS.firstYear && releaseYear <= latestYear)) {
      errors.releaseYear = `Release year must be between ${LIMITS.firstYear} and ${latestYear}`;
    }
  }

  const genreError = tooLong('Genre', genre, LIMITS.genre);
  if (genreError) errors.genre = genreError;
  const synopsisError = tooLong('Synopsis', synopsis, LIMITS.synopsis);
  if (synopsisError) errors.synopsis = synopsisError;

  if (Object.keys(errors).length) return { errors };
  return { input: { title, releaseYear, genre, synopsis }, errors };
}

export function validatePerson(values: PersonFormValues): { input?: PersonInput; errors: FieldErrors } {
  const name = values.name.trim();
  const bio = values.bio.trim();
  const errors: FieldErrors = {};

  if (!name) errors.name = 'Name is required';
  else if (tooLong('Name', name, LIMITS.name)) errors.name = tooLong('Name', name, LIMITS.name)!;
  if (values.role !== 'CAST' && values.role !== 'CREATOR') errors.role = 'Choose cast or creator';
  const bioError = tooLong('Bio', bio, LIMITS.bio);
  if (bioError) errors.bio = bioError;

  if (Object.keys(errors).length) return { errors };
  return { input: { name, role: values.role as Role, bio }, errors };
}

/** Returns an error message, or null when the file can be uploaded. */
export function validateArtwork(file: { size: number; type: string }): string | null {
  if (!ARTWORK_TYPES.includes(file.type)) return 'Choose a JPEG, PNG, GIF or WebP image';
  if (file.size === 0) return 'The file is empty';
  if (file.size > LIMITS.artworkBytes) return `The file is ${formatBytes(file.size)}. The limit is 5 MB.`;
  return null;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
