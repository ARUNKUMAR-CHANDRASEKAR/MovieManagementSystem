import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
export default {
  preprocess: vitePreprocess(),
  // Single-page app: every route is rendered in the browser, index.html is the fallback.
  kit: { adapter: adapter({ fallback: 'index.html' }) }
};
