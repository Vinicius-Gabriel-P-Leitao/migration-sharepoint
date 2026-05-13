import { defineConfig } from 'vite';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import babel from '@rolldown/plugin-babel';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), babel({ presets: [reactCompilerPreset()] })],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@lib': path.resolve(__dirname, './src/lib'),
      '@routes': path.resolve(__dirname, './src/routes'),
    },
  },
  server: {
    proxy: {
      '/v1': 'http://localhost:8080',
    },
  },
});
