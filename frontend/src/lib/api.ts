/** Error with a message that is safe to show to the user. */
export class ApiError extends Error {
  constructor(
    message: string,
    /** GraphQL error classification, e.g. NOT_FOUND or BAD_REQUEST. */
    readonly type?: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

const ENDPOINT = '/graphql';

type Variables = Record<string, unknown>;

/** Sends a GraphQL request. Pass an AbortSignal to cancel it (used when a newer search replaces it). */
export async function gql<T>(query: string, variables: Variables = {}, signal?: AbortSignal): Promise<T> {
  let response: Response;
  try {
    response = await fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, variables }),
      signal
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError('Cannot reach the server. Check your connection and try again.');
  }
  return parseResponse<T>(response.status, await response.text());
}

/**
 * Same as gql, but reports upload progress (0 to 1). fetch() cannot report upload progress,
 * so this uses XMLHttpRequest.
 */
export function gqlWithProgress<T>(
  query: string,
  variables: Variables,
  onProgress: (fraction: number) => void
): Promise<T> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', ENDPOINT);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded / e.total);
    };
    xhr.onload = () => {
      try {
        resolve(parseResponse<T>(xhr.status, xhr.responseText));
      } catch (e) {
        reject(e);
      }
    };
    xhr.onerror = () => reject(new ApiError('Upload failed. Check your connection and try again.'));
    xhr.send(JSON.stringify({ query, variables }));
  });
}

/** Turns an HTTP response into data, or throws an ApiError with the first GraphQL error. */
export function parseResponse<T>(status: number, text: string): T {
  let body: { data?: T | null; errors?: { message: string; extensions?: { classification?: string } }[] };
  try {
    body = JSON.parse(text);
  } catch {
    throw new ApiError(`Server error (HTTP ${status}). Please try again.`);
  }
  const error = body?.errors?.[0];
  if (error) throw new ApiError(error.message, error.extensions?.classification);
  if (status >= 400 || body?.data == null) throw new ApiError(`Server error (HTTP ${status}). Please try again.`);
  return body.data;
}

export function isAbort(e: unknown): boolean {
  return e instanceof DOMException && e.name === 'AbortError';
}

/** Reads a file as base64 (without the "data:...;base64," prefix). */
export function fileToBase64(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(stripDataUrl(reader.result as string));
    reader.onerror = () => reject(new ApiError('Could not read the file.'));
    reader.readAsDataURL(file);
  });
}

export function stripDataUrl(dataUrl: string): string {
  const comma = dataUrl.indexOf(',');
  return comma === -1 ? dataUrl : dataUrl.slice(comma + 1);
}
