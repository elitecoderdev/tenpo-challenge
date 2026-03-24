/**
 * EN: Application bootstrap file — the single entry point loaded by Vite.
 *     Responsibilities:
 *       1. Import custom fonts (Space Grotesk for UI text, Source Serif 4 for headings)
 *       2. Set up the React Query client with conservative defaults for the rate-limited API
 *       3. Wrap the App in StrictMode (double-invokes effects in dev to surface bugs early)
 *       4. Mount the React tree into the #root element
 *
 * ES: Archivo de bootstrap de la aplicación — el punto de entrada único cargado por Vite.
 *     Responsabilidades:
 *       1. Importar fuentes personalizadas (Space Grotesk para texto de UI, Source Serif 4 para encabezados)
 *       2. Configurar el cliente React Query con valores predeterminados conservadores para la API con límite de tasa
 *       3. Envolver la App en StrictMode (invoca efectos dos veces en dev para detectar bugs temprano)
 *       4. Montar el árbol React en el elemento #root
 */

// EN: Self-hosted font imports via @fontsource so the app works offline and in Docker without CDN access.
// ES: Importaciones de fuentes auto-alojadas via @fontsource para que la app funcione offline y en Docker sin acceso a CDN.
import '@fontsource/space-grotesk/400.css'
import '@fontsource/space-grotesk/500.css'
import '@fontsource/space-grotesk/700.css'
import '@fontsource/source-serif-4/400.css'
import '@fontsource/source-serif-4/700.css'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App.tsx'
import './index.css'

/**
 * EN: React Query client configured conservatively to respect the 3 req/min rate limit.
 *
 *     staleTime: 60_000 ms — fetched data is considered fresh for 1 minute.
 *       Data younger than staleTime will never trigger a background refetch,
 *       keeping the app well under the quota for normal usage.
 *
 *     gcTime: 10 * 60_000 ms — unused cache entries are garbage-collected after 10 minutes.
 *       Balances memory usage against the cost of a fresh fetch when the user navigates back.
 *
 *     retry: 0 — no automatic retries on failure.
 *       With only 3 requests per minute, retrying would rapidly exhaust the quota.
 *       Errors are surfaced to the user immediately; the manual refresh button handles re-sync.
 *
 *     refetchOnWindowFocus: false — suppress background refetch when the tab regains focus.
 *       Default React Query behavior would fire a request every time the user alt-tabs back,
 *       burning the rate-limit budget quickly.
 *
 *     refetchOnReconnect: false — suppress background refetch on network reconnect for the same reason.
 *
 * ES: Cliente React Query configurado de forma conservadora para respetar el límite de tasa de 3 req/min.
 *
 *     staleTime: 60_000 ms — los datos obtenidos se consideran frescos por 1 minuto.
 *       Los datos más nuevos que staleTime nunca desencadenarán un refetch en segundo plano,
 *       manteniendo la app bien por debajo de la cuota para uso normal.
 *
 *     gcTime: 10 * 60_000 ms — las entradas de cache no utilizadas se recolectan después de 10 minutos.
 *
 *     retry: 0 — sin reintentos automáticos en caso de fallo.
 *       Con solo 3 solicitudes por minuto, reintentar agotaría rápidamente la cuota.
 *
 *     refetchOnWindowFocus: false — suprime el refetch en segundo plano cuando la pestaña vuelve al foco.
 *     refetchOnReconnect: false — suprime el refetch en segundo plano al reconectar la red.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      gcTime: 10 * 60_000,
      retry: 0,
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
    },
  },
})

// EN: Mount the React application. The non-null assertion (!) is safe because index.html
//     always contains <div id="root"></div>; a missing element is a misconfiguration.
// ES: Montamos la aplicación React. La aserción no-null (!) es segura porque index.html
//     siempre contiene <div id="root"></div>; un elemento faltante es una mala configuración.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* EN: QueryClientProvider makes the query client available via the useQueryClient hook in any child component. / ES: QueryClientProvider hace que el cliente de consulta esté disponible via el hook useQueryClient en cualquier componente hijo. */}
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
