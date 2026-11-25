import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Port 5173 is already in the backend CORS allow-list (codepath.cors.allowed-origins).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
});
