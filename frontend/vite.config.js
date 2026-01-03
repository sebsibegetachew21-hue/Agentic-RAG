import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // Bind to IPv4 to avoid ::1 permission issues and use a non-default port.
  server: {
    host: '0.0.0.0',
    port: 5174,
  },
});
