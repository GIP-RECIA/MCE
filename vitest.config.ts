import { fileURLToPath } from 'node:url'
import { configDefaults, defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig({ command: 'serve', mode: 'test' }),
  defineConfig({
    test: {
      globals: true,
      root: fileURLToPath(new URL('./src/test/webapp/spec', import.meta.url)),
      setupFiles: ['./config/setup.ts'],
      environment: 'jsdom',
      exclude: [...configDefaults.exclude],
    },
  }),
)
