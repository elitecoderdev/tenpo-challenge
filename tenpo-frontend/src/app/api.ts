import axios from 'axios'

/**
 * EN: Resolves the API base URL from the Vite environment variable VITE_API_BASE_URL.
 *     The trim() call removes accidental leading/trailing whitespace that can appear
 *     when copying values from a .env file.
 *     Falls back to '/api' (relative path) when the variable is absent or blank,
 *     which works both in local Vite dev (proxied to http://localhost:8080) and in
 *     the production Docker container (served via Nginx which proxies /api → backend).
 *
 * ES: Resuelve la URL base de la API desde la variable de entorno Vite VITE_API_BASE_URL.
 *     El trim() elimina espacios accidentales al inicio/final que pueden aparecer
 *     al copiar valores de un archivo .env.
 *     Cae en '/api' (ruta relativa) cuando la variable está ausente o en blanco,
 *     lo que funciona tanto en el dev local de Vite (proxy a http://localhost:8080) como
 *     en el contenedor Docker de producción (servido via Nginx que hace proxy /api → backend).
 */
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() || '/api'

/**
 * EN: Shared Axios instance used by all API call functions in the application.
 *     Centralizing the instance here means:
 *       - The base URL is configured in one place (DRY principle).
 *       - Interceptors (e.g. auth headers, request logging) can be added here without
 *         modifying individual query files.
 *       - The Content-Type default ensures every request sends JSON without repeating
 *         the header in every call site.
 *
 * ES: Instancia Axios compartida usada por todas las funciones de llamada a API en la aplicación.
 *     Centralizar la instancia aquí significa:
 *       - La URL base se configura en un solo lugar (principio DRY).
 *       - Los interceptores (ej. encabezados de auth, logging de solicitudes) pueden agregarse
 *         aquí sin modificar los archivos de consulta individuales.
 *       - El Content-Type predeterminado asegura que cada solicitud envíe JSON sin repetir
 *         el encabezado en cada sitio de llamada.
 */
export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    // EN: Default Content-Type for all requests; can be overridden per-call if needed.
    // ES: Content-Type predeterminado para todas las solicitudes; puede sobreescribirse por llamada si se necesita.
    'Content-Type': 'application/json',
  },
})
