# Documentación del Sistema de Gestión Restaurante

## Descripción General

Sistema integral de gestión restaurantera con **3 componentes** que se sincronizan en tiempo real vía **Supabase (PostgreSQL)**:

- **App Android** (Kotlin/Jetpack Compose) — Toma de pedidos por meseros + monitor de cocina
- **Web KDS** (HTML/JS) — Pantalla de cocina con tablero Kanban
- **Web Caja + Auditoría** — Facturación, cobros y dashboard financiero

---

## Arquitectura

```
┌─────────────────────┐     ┌──────────────────────────┐
│  Android (Mesero)    │     │  Web-PC                  │
│  - Tomar pedidos     │     │  - Cocina (KDS Kanban)   │
│  - Ver cocina        │     │  - Caja (Facturación)    │
│  - Polling REST 3s   │     │  - Auditoría (KPIs/CSV)  │
└─────────┬────────────┘     └──────────┬───────────────┘
          │ HTTP REST                    │ Supabase JS SDK
          ▼                              ▼
┌──────────────────────────────────────────────────────┐
│                 Supabase (PostgreSQL)                  │
│  Tablas: pedidos, auditoria_financiera                 │
│  Realtime: WebSockets (publicación supabase_realtime)  │
└──────────────────────────────────────────────────────┘
```

## Tecnologías

| Capa | Tecnología |
|------|-----------|
| Android | Kotlin, Jetpack Compose, Material3, OkHttp, Moshi |
| Web | HTML5, CSS3 (Grid/Flexbox), Vanilla JS, Supabase JS SDK |
| BD | PostgreSQL 15+ (Supabase) |
| Tiempo real | Supabase Realtime (WebSockets/BroadcastChannel) |
| Build | Gradle Kotlin DSL, AGP 9.1.1, Kotlin 2.2.10 |

---

## Estructura del Proyecto

```
restaurant-main/
├── app/                          # Módulo Android
│   ├── build.gradle.kts          # CompileSdk 36, MinSdk 24, Jetpack, Compose
│   └── src/main/java/com/example/
│       ├── MainActivity.kt       # Entry point, inicializa repositorio
│       ├── data/
│       │   └── PedidoRepository.kt   # REST + mock local, polling 3s
│       └── ui/
│           ├── UIState.kt        # Modelos: MenuPlatillo, ItemCart, Pedido
│           ├── MeseroScreen.kt   # ~2600 líneas: UI completa mesero + cocina
│           └── theme/            # Color, Theme, Type (Material3)
│
├── web-pc/                       # Cliente web (sin framework)
│   ├── cocina.html               # KDS - Tablero Kanban 3 columnas
│   ├── caja.html                 # Facturación con cálculo de cambio
│   ├── auditoria.html            # KPIs financieros + tabla + CSV
│   └── assets/js/
│       ├── supabase-client.js    # DataService (abstrae Supabase / LocalStorage)
│       ├── app.js                # Controladores: Cocina, Caja, Auditoría, Settings
│       └── realtime-listeners.js # Suscripciones WebSocket + notificaciones
│
├── database/
│   └── esquema.sql               # Schema PostgreSQL completo
│
├── gradle/
│   └── libs.versions.toml        # Catálogo de versiones de dependencias
│
└── .env.example                  # Template para SUPABASE_URL, SUPABASE_ANON_KEY, GEMINI_API_KEY
```

---

## Base de Datos (PostgreSQL)

### Tabla `pedidos`
| Columna | Tipo | Descripción |
|---------|------|-------------|
| id | BIGINT (PK, auto) | Identificador único |
| mesa | VARCHAR(50) | Número/nombre de mesa |
| mesero | VARCHAR(100) | Nombre del mesero |
| items | JSONB | Array de `{producto, cantidad, precio, notas}` |
| total | NUMERIC(10,2) | Total de la cuenta |
| estado | VARCHAR(50) | `pendiente → cocinando → listo → entregado → pagado` |
| creado_en | TIMESTAMPTZ | Fecha de creación |
| actualizado_en | TIMESTAMPTZ | Última modificación |

### Tabla `auditoria_financiera`
| Columna | Tipo | Descripción |
|---------|------|-------------|
| id | BIGINT (PK, auto) | Identificador |
| pedido_id | BIGINT | FK al pedido cobrado |
| monto | NUMERIC(10,2) | Monto cobrado |
| metodo_pago | VARCHAR(50) | `efectivo`, `tarjeta`, `transferencia` |
| mesero | VARCHAR(100) | Mesero que atendió |
| creado_en | TIMESTAMPTZ | Fecha del cobro |

### Tiempo Real
Ambas tablas agregadas a la publicación `supabase_realtime` para WebSockets.

---

## API REST (Supabase)

| Método | Endpoint | Acción |
|--------|----------|--------|
| GET | `/rest/v1/pedidos?estado=neq.pagado` | Listar pedidos activos |
| POST | `/rest/v1/pedidos` | Crear pedido nuevo |
| PATCH | `/rest/v1/pedidos?id=eq.{id}` | Actualizar estado |
| POST | `/rest/v1/auditoria_financiera` | Registrar cobro |

---

## Flujo de Trabajo

1. **Mesero** (Android): Selecciona mesa → agrega platillos → envía comanda
2. **Cocina** (Web KDS): Ve pedido en "Nuevos" → "Empezar a Cocinar" → "Marcar Listo"
3. **Mesero** (Android): Ve notificación de "Listo" → sirve → marca "Entregado"
4. **Caja** (Web): Selecciona mesa → calcula cambio → cobra (efectivo/tarjeta/transferencia)
5. **Auditoría** (Web): KPIs actualizados en tiempo real, exportación CSV

---

## Configuración

### Android
1. Crear archivo `.env` en la raíz con:
```
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOi...
GEMINI_API_KEY=...
```
2. Las credenciales se inyectan vía Google Secrets Plugin a `BuildConfig`

### Web
1. Abrir cualquiera de los HTML en navegador
2. Click en ícono de engranaje ⚙️ para configurar Supabase
3. Los datos persisten en LocalStorage como fallback offline

---

## Mock / Demo Local

Sin configuración de Supabase, ambos clientes operan en modo **Demo**:

- **Android**: Datos en memoria con simulación automática de estados (cada 8s pasa a cocinando, 12s a listo, etc.)
- **Web**: Datos en `LocalStorage` con `BroadcastChannel` para sincronización entre pestañas
