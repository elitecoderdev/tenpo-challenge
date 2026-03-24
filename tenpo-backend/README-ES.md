# Tenpo Backend

API REST en Spring Boot 3 para el Tenpo Full Stack Challenge.
Expone un contrato HTTP limpio para CRUD de transacciones, aplica reglas de negocio,
persiste datos en PostgreSQL, documenta la API con Swagger y protege el servicio con
manejo estructurado de errores y límite de tasa por cliente.

---

## Tabla de contenidos

1. [Stack tecnológico](#1-stack-tecnológico)
2. [Estructura de paquetes y relaciones entre clases](#2-estructura-de-paquetes-y-relaciones-entre-clases)
3. [Diagramas de flujo de solicitudes](#3-diagramas-de-flujo-de-solicitudes)
4. [Reglas de dominio](#4-reglas-de-dominio)
5. [Modelo de datos](#5-modelo-de-datos)
6. [Resumen de la API](#6-resumen-de-la-api)
7. [Modelo de errores](#7-modelo-de-errores)
8. [Autenticación por API Key](#8-autenticación-por-api-key)
9. [Límite de tasa](#8b-límite-de-tasa)
10. [Análisis SOLID](#9-análisis-solid)
11. [Notas de seguridad](#10-notas-de-seguridad)
11. [Notas de rendimiento](#11-notas-de-rendimiento)
12. [Configuración](#12-configuración)
13. [Desarrollo local](#13-desarrollo-local)
14. [Docker](#14-docker)
15. [Tests](#15-tests)
16. [Verificación manual](#16-verificación-manual)
17. [Publicación en Docker Hub](#17-publicación-en-docker-hub)

---

## 1. Stack tecnológico

| Tecnología | Versión | Propósito |
|---|---|---|
| Java | 17 | Lenguaje / runtime |
| Spring Boot | 3.x | Framework, auto-configuración |
| Spring Web | — | DispatcherServlet, `@RestController` |
| Spring Validation | — | Bean Validation, `@Valid` |
| Spring Data JPA + Hibernate | — | ORM, queries derivadas |
| PostgreSQL | 16 | Base de datos principal |
| Flyway | — | Migraciones de esquema versionadas |
| springdoc-openapi | — | Swagger UI + JSON OpenAPI |
| Caffeine | — | Cache en memoria para contadores de rate limit |
| JUnit 5 + Spring Boot Test | — | Tests unitarios e integración |
| H2 (perfil test) | — | DB en memoria para tests (modo PostgreSQL) |

---

## 2. Estructura de paquetes y relaciones entre clases

### Estructura de paquetes

```
src/main/java/com/tenpo/challenge/
│
├── TenpobackApplication.java              ← Punto de entrada @SpringBootApplication
│
├── transaction/                           ← Agregado de dominio (toda la lógica CRUD)
│   ├── Transaction.java                   ← @Entity: columnas, @PrePersist/@PreUpdate
│   ├── TransactionRequest.java            ← DTO de entrada: anotaciones Bean Validation
│   ├── TransactionResponse.java           ← DTO de salida: excluye campos internos
│   ├── TransactionMapper.java             ← Entidad ↔ DTO (SRP: solo mapeo)
│   ├── TransactionRepository.java         ← Interfaz Spring Data JPA (queries derivadas)
│   ├── TransactionService.java            ← Lógica de negocio (cuota, sanitizar, convertir)
│   └── TransactionController.java         ← Endpoints REST (solo responsabilidades HTTP)
│
├── shared/
│   ├── api/
│   │   ├── ApiError.java                  ← Record inmutable de respuesta de error
│   │   ├── ApiErrorFactory.java           ← Construye ApiError con timestamp + razón
│   │   └── ApiFieldError.java             ← Record de error de validación por campo
│   └── exception/
│       ├── BusinessRuleException.java     ← Excepción de dominio que lleva HttpStatus
│       ├── ResourceNotFoundException.java ← Siempre mapea a HTTP 404
│       └── GlobalExceptionHandler.java    ← @RestControllerAdvice: 6 handlers
│
├── rate/
│   ├── ClientRateLimiter.java             ← Algoritmo de ventana fija (respaldado por Caffeine)
│   ├── RateLimitFilter.java               ← OncePerRequestFilter: aplica límite a /api/**
│   ├── RateLimitDecision.java             ← Value record: permitido/bloqueado + retryAfter
│   ├── ClientKeyResolver.java             ← Extrae IP del cliente (X-Forwarded-For)
│   └── RateLimitProperties.java           ← @ConfigurationProperties: capacidad, duración
│
└── config/
    ├── WebConfiguration.java              ← Lista blanca CORS (fail-closed si está vacía)
    ├── CorsProperties.java                ← @ConfigurationProperties: allowed-origins
    └── OpenApiConfiguration.java          ← Metadatos OpenAPI + config Swagger UI
```

### Grafo de dependencias entre clases

```
TenpobackApplication
    │ @SpringBootApplication + @EnableConfigurationProperties
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Pipeline HTTP                                                       │
│  RateLimitFilter ──▶ ClientKeyResolver (resuelve IP)                │
│       │              ClientRateLimiter (verifica/decrementa counter) │
│       │                 └── Caffeine Cache (FixedWindowCounter)      │
│       │ (bloqueado) ──▶ respuesta JSON 429                           │
│       │ (permitido) ──▶ DispatcherServlet                            │
└─────────────────────────────────────────────────────────────────────┘
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Capa de Controlador                                                 │
│  TransactionController (@RestController)                             │
│    POST /api/transactions       → create()                           │
│    GET  /api/transactions       → list()                             │
│    GET  /api/transactions/{id}  → get()                              │
│    PUT  /api/transactions/{id}  → update()                           │
│    DELETE /api/transactions/{id}→ delete()                           │
│                                                                      │
│  GlobalExceptionHandler (@RestControllerAdvice)                      │
│    captura excepciones de cualquier método @RestController           │
└─────────────────────────────────────────────────────────────────────┘
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Capa de Servicio                                                    │
│  TransactionService                                                  │
│    create()  → verificar cuota → sanitizar → guardar                │
│    update()  → buscar → sanitizar → guardar                         │
│    delete()  → buscar → eliminar                                     │
│    applyRequest() [privado] ← compartido por create y update        │
└─────────────────────────────────────────────────────────────────────┘
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Capa de Persistencia                                                │
│  TransactionRepository (JpaRepository<Transaction, Integer>)        │
│    findAll(Sort)                                                     │
│    findByCustomerNameNormalized(nombre, pageable)                    │
│    countByCustomerNameNormalized(nombre) ← usado para cuota         │
└─────────────────────────────────────────────────────────────────────┘
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Base de datos                                                       │
│  PostgreSQL 16 — esquema gestionado por Flyway                       │
│  Tabla: transactions                                                 │
│  Índices: idx_tx_date_id, idx_tx_customer_normalized                 │
└─────────────────────────────────────────────────────────────────────┘
```

### Flujo de manejo de excepciones

```
Cualquier método @RestController lanza...
  │
  ├── ResourceNotFoundException
  │     → 404 Not Found
  │
  ├── BusinessRuleException(status, mensaje)
  │     → status especificado por el llamador (ej. 409 Conflict)
  │
  ├── MethodArgumentNotValidException (Bean Validation)
  │     → 400 Bad Request + fieldErrors[]
  │
  ├── ConstraintViolationException
  │     → 400 Bad Request
  │
  ├── HttpMessageNotReadableException (JSON malformado)
  │     → 400 Bad Request
  │
  └── Exception (catch-all)
        → 500 Internal Server Error (mensaje genérico, sin stack trace)
```

---

## 3. Diagramas de flujo de solicitudes

### POST /api/transactions (flujo exitoso)

```
Cliente → POST /api/transactions { amountInPesos, merchant, customerName, transactionDate }
  │
  ▼ RateLimitFilter
  │  resolveKey(request) → "ip-del-cliente"
  │  limiter.tryConsume("ip-del-cliente") → RateLimitDecision.allowed(remaining)
  │  establece cabeceras X-Rate-Limit-*, continúa la cadena de filtros
  │
  ▼ TransactionController.create(@Valid @RequestBody TransactionRequest)
  │  Bean Validation ejecuta → si falla → GlobalExceptionHandler → 400
  │  llama a service.create(request)
  │
  ▼ TransactionService.create(request)
  │  canonicalize(request.customerName) → normalizado
  │  repo.countByCustomerNameNormalized(normalizado) → verifica < 100
  │  si >= 100 → lanza BusinessRuleException(409, "límite alcanzado")
  │  applyRequest(new Transaction(), request) → sanitiza + mapea campos
  │  @PrePersist establece customerNameNormalized
  │  repo.save(transaction) → PostgreSQL INSERT
  │  mapper.toResponse(guardado) → TransactionResponse
  │
  ▼ TransactionController.create (continuación)
  │  return ResponseEntity.created(location).body(response)
  │  Location: /api/transactions/{id}
  │
  ▼ Cliente recibe 201 Created + cuerpo JSON
```

### GET /api/transactions?customerName=X (flujo de listado)

```
Cliente → GET /api/transactions?customerName=Camila%20Torres
  │
  ▼ RateLimitFilter → permitido (decrementa contador)
  │
  ▼ TransactionController.list(@RequestParam Optional<String> customerName)
  │
  ▼ TransactionService.list(customerName)
  │  si customerName presente:
  │    canonicalize(customerName) → normalizado
  │    repo.findByCustomerNameNormalized(normalizado, Sort.by(fecha desc, id desc))
  │  si no:
  │    repo.findAll(Sort.by(fecha desc, id desc))
  │  mapper.toResponse(cada uno) → List<TransactionResponse>
  │
  ▼ 200 OK + array JSON
```

---

## 4. Reglas de dominio

| Regla | Dónde se aplica | Error |
|---|---|---|
| Monto ≥ 0 | `@Min(0)` en `TransactionRequest.amountInPesos` | 400 fieldError |
| Monto ≤ Integer.MAX_VALUE | `@Max(2147483647)` en `TransactionRequest.amountInPesos` | 400 fieldError |
| Comercio requerido, ≤ 160 caracteres | `@NotBlank @Size(max=160)` | 400 fieldError |
| Nombre de cliente requerido, ≤ 120 caracteres | `@NotBlank @Size(max=120)` | 400 fieldError |
| Fecha de transacción no en el futuro | `@PastOrPresent` | 400 fieldError |
| Máximo 100 transacciones por cliente | Verificación de cuota en `TransactionService.create()` | 409 Conflict |
| Monto no negativo a nivel de BD | `CHECK (amount_in_pesos >= 0)` | Restricción de BD (último recurso) |

### `customerNameNormalized` — por qué existe

Al comparar o contar nombres de clientes, usar `LOWER(customer_name)` en SQL impide que se usen los índices. En su lugar, se almacena una columna pre-computada `customer_name_normalized` con `minúsculas + espacios colapsados`. Los hooks JPA `@PrePersist` / `@PreUpdate` la mantienen sincronizada automáticamente en cada guardado.

---

## 5. Modelo de datos

```sql
CREATE TABLE transactions (
    id                       SERIAL       PRIMARY KEY,
    amount_in_pesos          INTEGER      NOT NULL CHECK (amount_in_pesos >= 0),
    merchant                 VARCHAR(160) NOT NULL,
    customer_name            VARCHAR(120) NOT NULL,
    customer_name_normalized  VARCHAR(120) NOT NULL,
    transaction_date         TIMESTAMP    NOT NULL
);

-- Cubre: ORDER BY transaction_date DESC, id DESC (listado por defecto)
CREATE INDEX idx_tx_date_id ON transactions (transaction_date DESC, id DESC);

-- Cubre: WHERE customer_name_normalized = ? (filtro + conteo de cuota)
CREATE INDEX idx_tx_customer_normalized ON transactions (customer_name_normalized);
```

---

## 6. Resumen de la API

Ruta base: `/api/transactions`

| Método | Ruta | Éxito | Condiciones de error |
|---|---|---|---|
| `GET` | `/api/transactions` | 200 array | 429 límite de tasa |
| `GET` | `/api/transactions?customerName=X` | 200 array filtrado | 429 |
| `GET` | `/api/transactions/{id}` | 200 único | 404, 429 |
| `POST` | `/api/transactions` | 201 + Location | 400 validación, 409 cuota, 429 |
| `PUT` | `/api/transactions/{id}` | 200 actualizado | 400 validación, 404, 429 |
| `DELETE` | `/api/transactions/{id}` | 204 No Content | 404, 429 |

Ejemplo de cuerpo de solicitud:

```json
{
  "amountInPesos": 15000,
  "merchant": "Supermercado Lider",
  "customerName": "Camila Torres",
  "transactionDate": "2026-03-06T11:30:00"
}
```

Swagger UI: `http://localhost:8080/swagger-ui`
JSON OpenAPI: `http://localhost:8080/v3/api-docs`

### Ejemplos con cURL

```bash
# Crear transacción
curl -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "amountInPesos": 21000,
    "merchant": "Restaurante",
    "customerName": "Camila Torres",
    "transactionDate": "2026-03-06T19:45:00"
  }'

# Listar todas
curl http://localhost:8080/api/transactions

# Filtrar por cliente
curl 'http://localhost:8080/api/transactions?customerName=Camila%20Torres'

# Obtener una por id
curl http://localhost:8080/api/transactions/1

# Actualizar
curl -X PUT http://localhost:8080/api/transactions/1 \
  -H 'Content-Type: application/json' \
  -d '{
    "amountInPesos": 24000,
    "merchant": "Restaurante actualizado",
    "customerName": "Camila Torres",
    "transactionDate": "2026-03-06T20:15:00"
  }'

# Eliminar
curl -X DELETE http://localhost:8080/api/transactions/1
```

---

## 7. Modelo de errores

```json
{
  "timestamp": "2026-03-06T19:45:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for the request body.",
  "path": "/api/transactions",
  "fieldErrors": [
    { "field": "amountInPesos", "message": "Transaction amount cannot be negative." }
  ]
}
```

`fieldErrors` solo está presente en errores de Bean Validation (400). El resto de errores tiene un array vacío. El handler de 500 devuelve un mensaje genérico — los stack traces se registran solo en el servidor.

---

## 8. Autenticación por API Key

Toda solicitud a `/api/**` debe incluir el header `X-API-Key`. El filtro corre con `@Order(1)`, antes que `RateLimitFilter`.

### Orden de la cadena de filtros

```
Solicitud → ApiKeyAuthFilter (@Order 1) → RateLimitFilter (@Order 2) → DispatcherServlet → Controller
```

### Comportamiento

| Escenario | Respuesta |
|---|---|
| Header `X-API-Key` ausente | HTTP 401 — cuerpo JSON estructurado `ApiError` |
| Header `X-API-Key` presente pero con valor incorrecto | HTTP 401 — cuerpo JSON `ApiError` (mensaje distinto al caso anterior) |
| Clave correcta | La solicitud continúa hacia `RateLimitFilter` |

### Rutas excluidas

`ApiKeyAuthFilter` **no** se aplica a:

- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/actuator/**`

### Configuración

| Propiedad | Variable de entorno | Valor por defecto |
|---|---|---|
| `app.security.api-key` | `APP_API_KEY` | `tenpo-dev-key` |

### Headers de seguridad

Los siguientes headers se agregan a **toda respuesta**, incluyendo las 401:

| Header | Valor |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Cache-Control` | `no-store` |

### Autenticación en Swagger UI

La Swagger UI (`/swagger-ui`) expone un botón "Authorize" (ícono de candado). Ingresa la API key allí para probar los endpoints autenticados desde el navegador.

---

## 8b. Límite de tasa

### Algoritmo: contador de ventana fija

```
Ventana: 60 segundos (PT1M por defecto)
Capacidad: 3 solicitudes por ventana por cliente

En cada solicitud a /api/**:
  1. Resolver clave del cliente (X-Forwarded-For → primera IP, o remote address)
  2. Cargar FixedWindowCounter desde el cache Caffeine (creado automáticamente por clave)
  3. Si ahora − windowStart ≥ duración → reiniciar contador, establecer windowStart = ahora
  4. Si count < capacidad → count++ → permitir (establecer X-Rate-Limit-Remaining)
  5. Si no → bloquear → devolver 429 JSON + cabecera Retry-After
```

### Cabeceras de respuesta (todas las solicitudes a /api/**)

| Cabecera | Valor |
|---|---|
| `X-Rate-Limit-Limit` | Capacidad configurada (por defecto 3) |
| `X-Rate-Limit-Remaining` | Solicitudes restantes en esta ventana |
| `Retry-After` | Segundos hasta que la ventana se reinicie (solo en 429) |

### Nota de seguridad: X-Forwarded-For

`ClientKeyResolver` confía en el primer valor de `X-Forwarded-For`. Un cliente puede falsificar esta cabecera. Para un challenge de un solo nodo esto es aceptable; en producción se debe configurar un proxy de confianza que sobreescriba la cabecera, o usar un API gateway dedicado.

---

## 9. Análisis SOLID

### SRP — Responsabilidad Única

Cada clase tiene exactamente una razón para cambiar:

- `TransactionController` — solo enrutamiento HTTP y códigos de estado. Cambia cuando cambia el contrato HTTP.
- `TransactionService` — solo reglas de negocio. Cambia cuando cambia la lógica de negocio.
- `TransactionRepository` — solo queries. Cambia cuando cambian los patrones de acceso a datos.
- `TransactionMapper` — solo mapeo entidad/DTO. Cambia cuando divergen las formas de los campos.
- `GlobalExceptionHandler` — solo forma de respuesta de error.
- `RateLimitFilter` — solo aplicación en el pipeline de solicitudes.
- `ClientRateLimiter` — solo algoritmo de contador.
- `ClientKeyResolver` — solo extracción de IP.

### OCP — Abierto/Cerrado

- `GlobalExceptionHandler` está abierto para extensión (nuevo método `@ExceptionHandler`) y cerrado para modificación (los handlers existentes no cambian).
- `BusinessRuleException` permite que cualquier nueva regla de dominio lance con cualquier HTTP status — sin necesidad de cambiar el handler.

### LSP — Sustitución de Liskov

- `ResourceNotFoundException` y `BusinessRuleException` extienden `RuntimeException`. Se comportan correctamente cuando son capturadas por cualquier handler de `RuntimeException`.

### ISP — Segregación de Interfaces

- `TransactionService` solo llama los métodos que necesita de `TransactionRepository`; no depende del contrato completo de `JpaRepository`.

### DIP — Inversión de Dependencias

- Todas las dependencias se inyectan por constructor (sin `@Autowired` en campos).
- `TransactionController` depende de `TransactionService` como bean de Spring, no de la clase concreta directamente.
- `RateLimitFilter` depende de los beans `ClientRateLimiter` y `ClientKeyResolver`.

---

## 10. Notas de seguridad

| Preocupación | Implementación |
|---|---|
| Autenticación por API key | `ApiKeyAuthFilter` (@Order 1): toda solicitud a `/api/**` requiere el header `X-API-Key`; 401 si falta o es incorrecta |
| CORS | Lista blanca desde variable de entorno; fail-closed si está vacía |
| Validación de entrada | Bean Validation + Zod (frontend); CHECK de BD (último recurso) |
| Filtración de información en errores | Handler de 500 devuelve mensaje genérico; sin stack trace en la respuesta |
| Bypass del rate limit | Falsificación de X-Forwarded-For es una limitación conocida y documentada |
| Inyección SQL | Queries parametrizadas JPA/Hibernate; sin SQL crudo en código de producción |
| Desbordamiento de entero | `Math.toIntExact()` en `TransactionService.applyRequest()` |

---

## 11. Notas de rendimiento

| Técnica | Efecto |
|---|---|
| `@Transactional(readOnly = true)` en list/get | Hibernate omite el dirty-checking; habilita routing a réplica de lectura |
| Índice compuesto `(transaction_date DESC, id DESC)` | Cubre el ORDER BY; sin full-table sort |
| Índice de una columna `(customer_name_normalized)` | Conteo de cuota es O(log n) por cliente |
| `customerNameNormalized` pre-computado | Índice usado sin llamada a función en runtime |
| Cache Caffeine para rate limit | Lecturas en memoria sub-microsegundos; sin round-trip a red externa |
| HikariCP (por defecto de Spring Boot) | Reutilización de conexiones BD; sin overhead de conexión por solicitud |

---

## 12. Configuración

| Variable | Propósito | Valor por defecto |
|---|---|---|
| `POSTGRES_DB` | Nombre de la BD | `tenpo_challenge` |
| `POSTGRES_USER` | Usuario de la BD | `tenpo` |
| `POSTGRES_PASSWORD` | Contraseña de la BD | `tenpo` |
| `POSTGRES_PORT` | Puerto del host para Postgres | `5432` |
| `DB_URL` | URL JDBC del datasource de Spring | `jdbc:postgresql://postgres:5432/tenpo_challenge` |
| `DB_USERNAME` | Usuario del datasource de Spring | `tenpo` |
| `DB_PASSWORD` | Contraseña del datasource de Spring | `tenpo` |
| `BACKEND_PORT` | Puerto del host para la API | `8080` |
| `APP_API_KEY` | API key requerida en el header `X-API-Key` | `tenpo-dev-key` |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes de navegador permitidos (separados por coma) | valores por defecto de localhost |
| `APP_RATE_LIMIT_CAPACITY` | Solicitudes por ventana | `3` |
| `APP_RATE_LIMIT_DURATION` | Duración de la ventana (ISO-8601) | `PT1M` |
| `BACKEND_IMAGE_NAME` | Nombre de la imagen Docker | `tenpo-backend` |
| `IMAGE_TAG` | Tag de la imagen Docker | `latest` |

Lista completa en [`.env.example`](.env.example) y [`src/main/resources/application.yml`](src/main/resources/application.yml).

---

## 13. Desarrollo local

### Prerrequisitos

- Java 17 o superior
- Una instancia de PostgreSQL corriendo (o Docker para la base de datos)

### Paso a paso

```bash
# 1. Clonar el repositorio (si aún no lo hiciste)
git clone <url-del-repo>
cd tenpo-backend

# 2. Copiar variables de entorno de ejemplo
cp .env.example .env
# Edita .env si necesitas cambiar usuario/contraseña/puerto

# 3. Levantar solo la base de datos con Docker (sin el stack completo)
docker compose up postgres -d
# Espera a que PostgreSQL esté listo (~5 segundos)

# 4. Ejecutar los tests (usa H2 en memoria, no necesita PostgreSQL)
./mvnw test

# 5. Iniciar la API
./mvnw spring-boot:run
```

La API estará disponible en: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui`

### Verificar que funciona

```bash
# Crear una transacción de prueba
curl -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "amountInPesos": 5000,
    "merchant": "Prueba Local",
    "customerName": "Usuario Test",
    "transactionDate": "2026-03-01T10:00:00"
  }'

# Listar transacciones
curl http://localhost:8080/api/transactions
```

### Detener

```bash
# Detener la API: Ctrl+C en la terminal donde corre mvnw

# Detener la base de datos
docker compose down
```

---

## 14. Docker

### Backend + base de datos (sin frontend)

```bash
# Desde la carpeta tenpo-backend
docker compose up --build
```

| Servicio | URL |
|---|---|
| API Backend | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui |
| PostgreSQL | localhost:5432 |

```bash
# Detener
docker compose down

# Detener y eliminar volumen de datos
docker compose down -v
```

Para el stack completo (incluyendo frontend), usa el [`docker-compose.yml`](../docker-compose.yml) de la raíz del proyecto.

### Variables de entorno en Docker

Crea un archivo `.env` en esta carpeta (basado en `.env.example`) para sobreescribir cualquier valor:

```bash
cp .env.example .env
# Edita el archivo .env según necesites
docker compose up --build
```

---

## 15. Tests

### Ejecutar todos los tests

```bash
./mvnw test
```

Los tests usan H2 en modo compatibilidad PostgreSQL — **no necesitas Docker ni una base de datos externa**.

### Descripción de cada suite de tests

| Clase de test | Alcance | Qué verifica |
|---|---|---|
| `TransactionServiceTest` | Unitario | Límite de cuota (99 vs 100), crear/actualizar/eliminar, canonicalización de nombre, guard de desbordamiento de entero |
| `TransactionRepositoryTest` | Integración (H2) | Queries derivadas, hook `@PrePersist`, filtro por nombre normalizado |
| `TransactionControllerTest` | Integración (MockMvc) | Códigos de estado HTTP, cabecera Location, forma de fieldErrors en 400, límite @Max |
| `RateLimitFilterIntegrationTest` | Integración (contexto completo) | Ciclo 3-permitidos + 4º-bloqueado, cabecera Retry-After, cuerpo JSON del 429 (envía header `X-API-Key`) |
| `ApiKeyAuthFilterIntegrationTest` | Integración (contexto completo) | 6 casos: clave ausente → 401, clave incorrecta → 401, clave correcta → pasa, Swagger/Actuator excluidos |

### Ejecutar un test específico

```bash
# Solo el servicio
./mvnw test -Dtest=TransactionServiceTest

# Solo los tests de rate limit
./mvnw test -Dtest=RateLimitFilterIntegrationTest

# Solo el controlador
./mvnw test -Dtest=TransactionControllerTest
```

---

## 16. Verificación manual

1. Iniciar el stack: `docker compose up --build`
2. Abrir `http://localhost:8080/swagger-ui`
3. Crear una transacción (POST)
4. Obtener la lista (GET)
5. Actualizar la transacción (PUT)
6. Eliminarla (DELETE)
7. Probar errores de validación:
   - `amountInPesos` negativo
   - `transactionDate` en el futuro
   - Crear más de 100 transacciones para el mismo cliente
8. Activar el límite de tasa: llamar cualquier endpoint `/api/**` más de 3 veces en 60 segundos

---

## 17. Publicación en Docker Hub

```bash
# 1. Copiar y configurar variables de entorno
cp .env.example .env
# Establecer: BACKEND_IMAGE_NAME=<tu-usuario>/tenpo-backend

# 2. Construir la imagen
docker compose build backend

# 3. Publicar
docker push <tu-usuario>/tenpo-backend:latest
```
