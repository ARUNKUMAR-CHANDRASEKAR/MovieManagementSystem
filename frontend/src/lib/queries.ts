// GraphQL documents used by the pages. Kept in one place so the API surface is easy to review.

const MOVIE_FIELDS = 'id title releaseYear genre synopsis region artworkUrl';
const PERSON_FIELDS = 'id name role bio';

export const MOVIES = `query Movies($search: String, $region: Region, $page: Int, $size: Int) {
  movies(search: $search, region: $region, page: $page, size: $size) { totalCount page size items { ${MOVIE_FIELDS} } }
}`;

export const MOVIE = `query Movie($id: ID!) {
  movie(id: $id) { ${MOVIE_FIELDS} people { ${PERSON_FIELDS} } }
}`;

export const CREATE_MOVIE = `mutation CreateMovie($input: MovieInput!) { createMovie(input: $input) { id } }`;

export const UPDATE_MOVIE = `mutation UpdateMovie($id: ID!, $input: MovieInput!) {
  updateMovie(id: $id, input: $input) { ${MOVIE_FIELDS} people { ${PERSON_FIELDS} } }
}`;

export const DELETE_MOVIE = `mutation DeleteMovie($id: ID!) { deleteMovie(id: $id) }`;

export const UPLOAD_ARTWORK = `mutation UploadArtwork($movieId: ID!, $dataBase64: String!) {
  uploadArtwork(movieId: $movieId, dataBase64: $dataBase64) { id artworkUrl }
}`;

export const ADD_CREDIT = `mutation AddCredit($movieId: ID!, $personId: ID!) {
  addCredit(movieId: $movieId, personId: $personId) { id people { ${PERSON_FIELDS} } }
}`;

export const REMOVE_CREDIT = `mutation RemoveCredit($movieId: ID!, $personId: ID!) {
  removeCredit(movieId: $movieId, personId: $personId) { id people { ${PERSON_FIELDS} } }
}`;

export const PEOPLE = `query People($search: String, $role: PersonRole, $page: Int, $size: Int) {
  people(search: $search, role: $role, page: $page, size: $size) { totalCount page size items { ${PERSON_FIELDS} } }
}`;

export const PERSON = `query Person($id: ID!) {
  person(id: $id) { ${PERSON_FIELDS} movies { ${MOVIE_FIELDS} } }
}`;

export const CREATE_PERSON = `mutation CreatePerson($input: PersonInput!) { createPerson(input: $input) { id } }`;

export const UPDATE_PERSON = `mutation UpdatePerson($id: ID!, $input: PersonInput!) {
  updatePerson(id: $id, input: $input) { ${PERSON_FIELDS} }
}`;

export const DELETE_PERSON = `mutation DeletePerson($id: ID!) { deletePerson(id: $id) }`;

// Movies and people in one request.
export const SEARCH_ALL = `query SearchAll($search: String) {
  movies(search: $search, size: 12) { totalCount items { ${MOVIE_FIELDS} } }
  people(search: $search, size: 12) { totalCount items { ${PERSON_FIELDS} } }
}`;
