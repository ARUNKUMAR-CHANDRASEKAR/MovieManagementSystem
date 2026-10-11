export type Role = 'CAST' | 'CREATOR';

export type Region = 'SINGAPORE' | 'INDIA' | 'HOLLYWOOD';

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
  region: Region | null;
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
  region: Region | null;
}

export interface PersonInput {
  name: string;
  role: Role;
  bio: string;
}

export const REGION_LABEL: Record<Region, string> = { SINGAPORE: 'Singapore', INDIA: 'India', HOLLYWOOD: 'Hollywood' };

export const ROLE_LABEL: Record<Role, string> = { CAST: 'Cast', CREATOR: 'Creator' };
