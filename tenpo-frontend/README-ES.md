# Tenpo Frontend

SPA en React 19 + TypeScript para el Tenpo Full Stack Challenge.
Provee una interfaz de gestión de transacciones responsiva, valida la entrada antes
del envío, consume el backend con Axios y se mantiene eficiente bajo el estricto
límite de tasa por cliente del challenge.

---

## Tabla de contenidos

1. [Stack tecnológico](#1-stack-tecnológico)
2. [Mapa de módulos y árbol de componentes](#2-mapa-de-módulos-y-árbol-de-componentes)
3. [Estado y flujo de datos](#3-estado-y-flujo-de-datos)
4. [Estrategia de cache con React Query](#4-estrategia-de-cache-con-react-query)
5. [Reglas de validación](#5-reglas-de-validación)
6. [Análisis DRY](#6-análisis-dry)
7. [Autenticación por API Key](#7-autenticación-por-api-key)
8. [Notas de rendimiento](#7b-notas-de-rendimiento)
9. [Configuración de runtime](#8-configuración-de-runtime)
9. [Desarrollo local](#9-desarrollo-local)
10. [Docker](#10-docker)
11. [Flujo de QA manual](#11-flujo-de-qa-manual)
12. [Alineación con el challenge](#12-alineación-con-el-challenge)
13. [Publicación de imagen Docker](#13-publicación-de-imagen-docker)

---

## 1. Stack tecnológico

| Tecnología | Versión | Propósito |
|---|---|---|
| React | 19 | Framework de UI |
| TypeScript | 5.x | Seguridad de tipos |
| Vite | 6.x | Servidor de desarrollo + bundler |
| Axios | — | Cliente HTTP |
| `@tanstack/react-query` | 5.x | Cache de estado del servidor |
| `react-hook-form` | — | Formularios controlados |
| `zod` | — | Validación de esquemas |
| `dayjs` | — | Formateo de fechas |
| `@fontsource/*` | — | Fuentes auto-alojadas (funciona offline) |
| Nginx | alpine | Servicio de archivos estáticos en producción |

---

## 2. Mapa de módulos y árbol de componentes

### Estructura de directorios

```
src/
├── main.tsx                           ← Bootstrap: QueryClient, StrictMode, mount
├── App.tsx                            ← Raíz: estado, mutaciones, orquestación de layout
├── App.css                            ← Estilos globales de página
├── index.css                          ← Reset CSS + design tokens (variables)
│
├── app/
│   └── api.ts                         ← Instancia compartida de Axios, resolución de base URL
│
├── components/
│   └── Modal.tsx                      ← Modal genérico accesible (createPortal + ARIA)
│
├── features/
│   └── transactions/
│       ├── types.ts                   ← Interfaces TypeScript: Transaction, ApiError, …
│       ├── schema.ts                  ← Esquema Zod + getInitialFormValues + toPayload
│       ├── queries.ts                 ← transactionKeys, opciones de query, fns de mutación
│       ├── TransactionList.tsx        ← Renderizador de lista de tarjetas (stringToHue, getInitials)
│       └── TransactionForm.tsx        ← Formulario controlado (modo dual: crear + editar)
│
└── lib/
    └── formatters.ts                  ← formatCLP() — formateador CLP compartido (corrección DRY)
```

### Árbol de componentes

```
App (raíz)
│
├── [sección hero]
│   ├── Botón "Nueva transacción" → handleCreateMode()
│   └── Botón "Actualizar" → refetch()
│
├── [grilla de estadísticas]
│   ├── Conteo de movimientos visibles
│   ├── Monto visible (formatCLP)
│   ├── Conteo de Tenpistas únicos
│   └── Fecha y cliente del último movimiento
│
├── [workspace]
│   ├── [panel--list]
│   │   ├── input de filtro por cliente
│   │   ├── banner de error de lista (en caso de fallo del fetch)
│   │   ├── [skeleton-list] (mientras isLoading)
│   │   └── TransactionList
│   │       └── [transaction-card ×N]
│   │           ├── badge de avatar (iniciales, color hsl del hash del comercio)
│   │           ├── comercio + cliente + píldora de id
│   │           ├── monto (formatCLP) + fecha (dayjs)
│   │           ├── Botón Editar → onEdit(transaction)
│   │           └── Botón Eliminar → onDelete(transaction)
│   │
│   └── [workspace__side]
│       ├── [panel--form] (cuando activeTransaction está establecido)
│       │   ├── TransactionForm (modo edición)
│       │   └── note-card (explicación de estrategia de cache)
│       └── [panel--editor-empty] (cuando no hay activeTransaction)
│           ├── Texto "Elige un registro o empieza desde cero"
│           ├── Botón "Nueva transacción"
│           └── note-card (explicación de UX del modal)
│
├── Modal (createPortal → document.body)
│   └── [create-flow]
│       ├── hero + píldoras de reglas
│       └── TransactionForm (modo crear, variant="modal")
│
└── [toast] (notificación de éxito efímera)
```

---

## 3. Estado y flujo de datos

### Estado de App

```
Variable de estado      Tipo                    Descripción
─────────────────────── ──────────────────────  ─────────────────────────────────────────
transactions            Transaction[]           Cache de React Query (fetch una vez al cargar)
activeTransaction       Transaction | null      Tarjeta abierta actualmente en el panel lateral
isCreateModalOpen       boolean                 Controla la visibilidad del Modal
serverError             ApiError | null         Último error de mutación fallida → enviado al formulario
draftCustomerFilter     string                  Valor crudo del input (estado de alta prioridad)
deferredCustomerFilter  string                  Copia diferida (baja prioridad, para filtrar)
toast                   string | null           Mensaje de éxito efímero (auto-ocultar en 3 s)
isPanelPending          boolean                 True mientras el lote de useTransition se procesa
```

### Valores derivados (sin solicitudes adicionales)

```
visibleTransactions  = transactions.filter(tx => tx.customerName incluye deferredFilter)
totalAmount          = suma de visibleTransactions.amountInPesos
uniqueCustomers      = conteo de customerName distintos (minúsculas) en visibleTransactions
latestTransaction    = visibleTransactions[0]  (la lista está ordenada más reciente primero)
```

### Flujo de mutaciones

```
Usuario envía el formulario
  │
  ├─ activeTransaction está establecido → updateMutation.mutateAsync({id, payload})
  └─ activeTransaction es null → createMutation.mutateAsync(payload)
          │
          ▼
  Llamada a API (POST o PUT /api/transactions[/{id}])
          │
          ├─ onSuccess: queryClient.setQueryData() → cache actualizado, sin refetch
          │             showToast("Transacción …")
          │             setActiveTransaction(resultado)
          │
          └─ onError:   extractApiError() → setServerError()
                        TransactionForm muestra errores de campo inline o banner
```

---

## 4. Estrategia de cache con React Query

El límite de tasa del challenge es `3 solicitudes / minuto / cliente`.
Una SPA por defecto (refetch en focus, reconexión, después de cada mutación) agotaría la cuota inmediatamente. Cada opción del QueryClient es una elección deliberada:

| Opción | Valor | Razón |
|---|---|---|
| `staleTime` | `60 000` ms | Los datos están frescos 1 min → sin refetch en segundo plano en ese período |
| `gcTime` | `600 000` ms | Cache no utilizado sobrevive 10 min → volver a la página no re-fetcha |
| `retry` | `0` | Cada reintento consume 1 de 3 solicitudes/min; los errores se muestran inmediatamente |
| `refetchOnWindowFocus` | `false` | Alt-tabear de regreso dispararía una solicitud cada vez |
| `refetchOnReconnect` | `false` | Reconectar la red dispararía una solicitud automáticamente |

Después de mutaciones: `queryClient.setQueryData()` actualiza el cache en lugar → sin solicitud de red.
Sincronización manual: el botón "Actualizar" llama a `refetch()` para una sincronización deliberada.

---

## 5. Reglas de validación

El esquema Zod en `schema.ts` aplica las mismas reglas que el backend:

| Campo | Regla | Mensaje de error |
|---|---|---|
| `amountInPesos` | Entero requerido | "Amount must be an integer." |
| `amountInPesos` | ≥ 0 | "Amount cannot be negative." |
| `amountInPesos` | ≤ 2 147 483 647 | "Amount must stay below 2,147,483,647." |
| `merchant` | Requerido (no vacío tras trim) | "Merchant is required." |
| `merchant` | ≤ 160 caracteres | "Merchant must stay under 160 characters." |
| `customerName` | Requerido (no vacío tras trim) | "Tenpista name is required." |
| `customerName` | ≤ 120 caracteres | "Tenpista name must stay under 120 characters." |
| `transactionDate` | Datetime válido | "Use a valid date and time." |
| `transactionDate` | No en el futuro | "Transaction date cannot be in the future." |

El input `datetime-local` también establece `max={new Date().toISOString().slice(0,16)}` para prevención de fecha futura nativa del navegador (accesible, sin JS requerido).

El backend re-valida todo de forma independiente — la API es segura incluso sin la UI.

---

## 6. Análisis DRY

### `lib/formatters.ts` — formateador CLP compartido

**Problema**: `Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 })` se instanciaba de forma independiente en `App.tsx` y `TransactionList.tsx`. Un cambio de locale u opciones requeriría editar dos archivos y riesgo de divergencia.

**Solución**: extraído a `src/lib/formatters.ts` como `formatCLP(amount: number): string`. Ambos archivos ahora importan la función compartida. El objeto `Intl.NumberFormat` es una constante a nivel de módulo — creada una sola vez al importar, no en cada render.

### Factory `transactionKeys` (`queries.ts`)

Todas las operaciones de cache de React Query referencian `transactionKeys.all`. Cambiar la clave del cache requiere editar una línea.

### `toPayload()` y `getInitialFormValues()` (`schema.ts`)

Definidos una vez, llamados desde `TransactionForm` (estado inicial, reset) y `App.tsx` (callback de submit). La normalización de espacios (`.trim().replace(/\s+/g, ' ')`) refleja el `sanitizeText()` del backend en un solo lugar.

### `normalizeApiError()` (`App.tsx`)

Los tres handlers `onError` de mutación llaman a `extractApiError()` → `normalizeApiError()`. La lógica de conversión de errores no se repite.

---

## 7. Autenticación por API Key

El frontend lee la API key desde la variable de entorno `VITE_API_KEY` (valor por defecto: `tenpo-dev-key`). La instancia de Axios en `src/app/api.ts` agrega el header `X-API-Key` a **cada solicitud** automáticamente mediante un interceptor de request.

```
VITE_API_KEY está definido (ej. en .env)
  → usar ese valor como header X-API-Key

VITE_API_KEY no está definido
  → usar "tenpo-dev-key" como valor por defecto
```

El archivo `.env.example` incluye `VITE_API_KEY=tenpo-dev-key`. Define esta variable antes de ejecutar en un entorno donde el backend usa una clave diferente.

---

## 7b. Notas de rendimiento

| Técnica | Dónde | Efecto |
|---|---|---|
| Fetch una vez, filtrar localmente | `App.tsx` | Cero solicitudes extra para el filtro por cliente |
| `useDeferredValue` | `App.tsx` | El input de filtro sigue responsivo mientras la lista se re-renderiza |
| `useTransition` para el estado del panel | `App.tsx` | Abrir/cerrar el panel no bloquea renders urgentes |
| Actualizaciones optimistas del cache | `onSuccess` de mutaciones | `setQueryData()` en lugar de `refetch()` |
| `Intl.NumberFormat` a nivel de módulo | `lib/formatters.ts` | Creado una vez al importar, reutilizado en todos los renders |
| Escalonamiento de animación CSS vía `--stagger` | `TransactionList.tsx` | Calculado en CSS desde una propiedad personalizada — sin bucle de animación JS |
| El hue del comercio se calcula, no se almacena | `TransactionList.tsx` | `stringToHue()` corre en render; no se necesita campo extra en la API |
| Fuentes auto-alojadas (`@fontsource`) | `main.tsx` | Funciona offline y en Docker sin CDN |
| `react-hook-form` | `TransactionForm.tsx` | Inputs no controlados con mínimos re-renders |
| `useMemo` para valores derivados | `App.tsx` | `visibleTransactions`, `totalAmount`, `uniqueCustomers` solo se recomputan cuando cambian sus dependencias |
| `useCallback` para event handlers | `App.tsx` | Referencias de función estables que evitan re-renders innecesarios en componentes hijos |
| `React.memo` en lista y formulario | `TransactionList`, `TransactionForm` | Los componentes omiten el re-render cuando sus props no cambian |
| `manualChunks` en `vite.config.ts` | Salida del build | Librerías de vendor divididas en `vendor-react`, `vendor-query`, `vendor-utils`, `vendor-forms` — mejor aprovechamiento del cache del navegador entre deploys |

---

## 8. Configuración de runtime

| Variable | Propósito | Valor por defecto |
|---|---|---|
| `FRONTEND_PORT` | Puerto del host para el contenedor | `3000` |
| `VITE_API_BASE_URL` | URL base para el build de producción | `""` (usa `/api` relativo) |
| `VITE_API_KEY` | API key enviada como `X-API-Key` en cada solicitud | `tenpo-dev-key` |
| `FRONTEND_IMAGE_NAME` | Nombre de la imagen Docker | `tenpo-frontend` |
| `IMAGE_TAG` | Tag de la imagen Docker | `latest` |

### Lógica de resolución de la URL base (`src/app/api.ts`)

```
VITE_API_BASE_URL está definido (Docker repo-level, ej. http://localhost:8080/api)
  → usar ese valor

VITE_API_BASE_URL está vacío (Docker full-stack en raíz + Vite dev local)
  → usar /api relativo
  → Nginx proxea /api/* → http://backend:8080  (Docker full-stack)
  → Vite dev server proxea /api/* → http://localhost:8080  (dev local)
```

Lista completa: [`.env.example`](.env.example)

---

## 9. Desarrollo local

### Prerrequisitos

- Node.js 20 o superior (o LTS reciente)
- Backend corriendo en `http://localhost:8080`

### Paso a paso

```bash
# 1. Clonar el repositorio (si aún no lo hiciste)
git clone <url-del-repo>
cd tenpo-frontend

# 2. Instalar dependencias
npm install

# 3. Copiar variables de entorno de ejemplo
cp .env.example .env
# No es necesario editar .env para dev local (Vite proxea automáticamente)

# 4. Iniciar el servidor de desarrollo
npm run dev
```

La app estará disponible en: `http://localhost:5173`

Vite proxea automáticamente `/api` → `http://localhost:8080` en desarrollo, por lo que no necesitas configurar CORS manualmente.

### Verificaciones de calidad

```bash
# ESLint — verifica reglas de estilo y errores estáticos
npm run lint

# Build de producción — compila TypeScript + genera bundle optimizado
npm run build

# Previsualizar el build de producción localmente
npm run preview   # → http://localhost:4173
```

### Detener

```bash
# Detener Vite: Ctrl+C en la terminal donde corre npm run dev
```

---

## 10. Docker

### Frontend solo (requiere backend en `http://localhost:8080`)

```bash
# Desde la carpeta tenpo-frontend
docker compose up --build
```

| Servicio | URL |
|---|---|
| Frontend | http://localhost:3000 |

Usa [`nginx.standalone.conf`](nginx.standalone.conf) — sirve la SPA y proxea `/api` directamente a `http://localhost:8080/api`.

```bash
# Detener
docker compose down
```

Para el stack completo (frontend + backend + base de datos), usa el [`docker-compose.yml`](../docker-compose.yml) de la raíz del proyecto:

```bash
# Desde la raíz del proyecto
cd ..
docker compose up --build
```

| Servicio | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui |
| PostgreSQL | localhost:5432 |

### Variables de entorno en Docker

```bash
cp .env.example .env
# Edita .env si necesitas cambiar puertos o la imagen base
docker compose up --build
```

---

## 11. Flujo de QA manual

1. Iniciar el backend (`cd tenpo-backend && docker compose up --build`).
2. Iniciar este frontend (`docker compose up --build` o `npm run dev`).
3. Abrir `http://localhost:3000` (o `http://localhost:5173` para dev local).
4. Crear una transacción desde el modal "Nueva transacción".
5. Confirmar que aparece en la lista inmediatamente (actualización de cache, sin refetch).
6. Hacer clic en Editar → modificar un campo → Guardar cambios.
7. Eliminar la transacción → confirmar que desaparece de la lista.
8. Probar validaciones:
   - Monto negativo → error inline debajo del campo
   - Fecha futura → error inline debajo del campo
   - Comercio vacío → error inline debajo del campo
9. Activar un 429 del backend haciendo clic en Actualizar más de 3 veces en 60 s → confirmar que el banner de error aparece en el panel.
10. Redimensionar la ventana del navegador — confirmar que el layout se adapta para móvil.

---

## 12. Alineación con el challenge

| Requisito del challenge | Implementación |
|---|---|
| Aplicación React | Vite + React 19 + TypeScript |
| Interfaz moderna y responsiva | CSS Grid + custom properties, breakpoints para móvil |
| Fetch con Axios | Instancia compartida de Axios en `src/app/api.ts` |
| Uso de `react-query` | `@tanstack/react-query` v5, `useQuery`, `useMutation` |
| Validación de formulario antes del envío | Esquema `zod` + `react-hook-form` |
| Panel de cliente con listado | `TransactionList` con filtro y estadísticas |
| Formulario de agregar/editar | `TransactionForm` (modo dual: crear en modal, editar en panel lateral) |

Valor adicional más allá del mínimo:

- Notificaciones toast para cada resultado de mutación
- Errores de campo del servidor mapeados de vuelta al estado del formulario (display inline)
- Normalizador de errores robusto que maneja formas de error de API parciales/inválidas
- `useDeferredValue` + `useTransition` para filtrado fluido bajo carga
- Identidad de color por comercio (hue derivado del hash del nombre — consistente entre re-renders)

---

## 13. Publicación de imagen Docker

```bash
# 1. Copiar y configurar variables de entorno
cp .env.example .env
# Establecer: FRONTEND_IMAGE_NAME=<tu-usuario>/tenpo-frontend

# 2. Construir la imagen
docker compose build frontend

# 3. Publicar
docker push <tu-usuario>/tenpo-frontend:latest
```
