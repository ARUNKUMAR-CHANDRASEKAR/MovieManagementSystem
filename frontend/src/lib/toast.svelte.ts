export interface Toast {
  id: number;
  kind: 'success' | 'error';
  message: string;
}

/** Messages shown in the corner of the screen. Read by Toasts.svelte. */
export const toasts = $state<Toast[]>([]);

let nextId = 1;

export function notify(kind: Toast['kind'], message: string, ms = 4000) {
  const id = nextId++;
  toasts.push({ id, kind, message });
  setTimeout(() => dismiss(id), ms);
}

export function dismiss(id: number) {
  const index = toasts.findIndex((t) => t.id === id);
  if (index !== -1) toasts.splice(index, 1);
}
