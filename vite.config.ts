import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath } from 'node:url';

const srcRoot = fileURLToPath(new URL('./src/main/resources/static/src', import.meta.url));

export default defineConfig({
  root: srcRoot,
  base: './',
  plugins: [vue()],
  build: {
    outDir: fileURLToPath(new URL('./target/classes/static', import.meta.url)),
    emptyOutDir: true
  }
});