import { describe, expect, it } from 'vitest';
import { ApiError, parseResponse, stripDataUrl } from './api';

describe('parseResponse', () => {
  it('returns data', () => {
    expect(parseResponse(200, '{"data":{"movie":{"id":"1"}}}')).toEqual({ movie: { id: '1' } });
  });

  it('throws the first GraphQL error with its type', () => {
    const body = JSON.stringify({
      data: null,
      errors: [
        { message: 'Movie 4 not found', extensions: { classification: 'NOT_FOUND' } },
        { message: 'second' }
      ]
    });
    expect(() => parseResponse(200, body)).toThrowError(
      expect.objectContaining({ message: 'Movie 4 not found', type: 'NOT_FOUND' })
    );
  });

  it('throws a friendly error for non-JSON responses (e.g. a proxy error page)', () => {
    expect(() => parseResponse(502, '<html>Bad gateway</html>')).toThrowError('Server error (HTTP 502). Please try again.');
  });

  it('throws when an HTTP error has no GraphQL errors', () => {
    expect(() => parseResponse(500, '{}')).toThrowError('Server error (HTTP 500)');
  });

  it('throws when data is missing', () => {
    expect(() => parseResponse(200, '{"data":null}')).toThrowError(ApiError);
  });

  it('handles a JSON null body', () => {
    expect(() => parseResponse(200, 'null')).toThrowError(ApiError);
  });
});

describe('stripDataUrl', () => {
  it('removes the data URL prefix', () => {
    expect(stripDataUrl('data:image/png;base64,iVBORw0')).toBe('iVBORw0');
  });

  it('returns the text unchanged when there is no prefix', () => {
    expect(stripDataUrl('iVBORw0')).toBe('iVBORw0');
  });
});
