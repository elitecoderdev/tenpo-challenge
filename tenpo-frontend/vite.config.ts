import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 4173,
  },
  build: {
    rollupOptions: {
      output: {
        // EN: Split heavy third-party libraries into separate chunks so the browser can cache them
        //     independently from the application code. When only app code changes, the vendor
        //     chunk keeps its cache fingerprint — users only re-download what actually changed.
        // ES: Dividimos las bibliotecas de terceros pesadas en chunks separados para que el navegador
        //     pueda cachearlos independientemente del código de la aplicación. Cuando solo cambia el
        //     código de la app, el chunk vendor mantiene su huella de caché — los usuarios solo
        //     vuelven a descargar lo que realmente cambió.
        manualChunks: {
          // EN: React runtime — rarely changes between deployments.
          // ES: Runtime de React — raramente cambia entre despliegues.
          'vendor-react': ['react', 'react-dom'],
          // EN: TanStack Query — stable query/cache layer.
          // ES: TanStack Query — capa de query/caché estable.
          'vendor-query': ['@tanstack/react-query'],
          // EN: Axios HTTP client + day.js date library.
          // ES: Cliente HTTP Axios + biblioteca de fechas day.js.
          'vendor-utils': ['axios', 'dayjs'],
          // EN: Zod schema validation + react-hook-form + the Zod resolver bridge.
          // ES: Validación de esquemas Zod + react-hook-form + el puente resolver de Zod.
          'vendor-forms': ['zod', 'react-hook-form', '@hookform/resolvers'],
        },
      },
    },
  },
})
