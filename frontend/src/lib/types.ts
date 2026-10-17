export type Role = 'CAST' | 'CREATOR';

export interface Person {
  id: string;
  name: string;
  role: Role;
  bio: string;
  movies?: Movie[];
}

export interface Movie {
  id: string;
  title: string;
  releaseYear: number | null;
  genre: string;
  synopsis: string;
  artworkUrl: string | null;
  people?: Person[];
}

export interface Page<T> {
  items: T[];
  totalCount: number;
  page: number;
  size: number;
}

export interface MovieInput {
  title: string;
  releaseYear: number | null;
  genre: string;
  synopsis: string;
}

export interface PersonInput {
  name: string;
  role: Role;
  bio: string;
}

export const ROLE_LABEL: Record<Role, string> = { CAST: 'Cast', CREATOR: 'Creator' };
