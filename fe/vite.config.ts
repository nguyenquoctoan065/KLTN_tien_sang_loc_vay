import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Khi chạy với backend thật (VITE_USE_MOCK=false), /api được chuyển tới Spring Boot ở cổng 8080
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
  build: {
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: { antd: ['antd', '@ant-design/icons', 'dayjs'], echarts: ['echarts', 'echarts-for-react'], vendor: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query', 'axios'] },
      },
    },
  },
});
