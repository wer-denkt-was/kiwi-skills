import {fileURLToPath, URL} from 'node:url'
import {defineConfig} from 'vite'
import Vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import {KiwiResolver} from '@werdenktwas/kiwi-vue/tooling'

export default defineConfig({
  plugins: [
    Vue(),
    Components({
      resolvers: [KiwiResolver()]
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
})
