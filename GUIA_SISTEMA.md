# 📖 Guía del Sistema de Gestión de Restaurante (EN COCINA KDS)

Este documento detalla las funcionalidades, el uso de la interfaz y la arquitectura del sistema implementado para **EN COCINA**.

---

## 🚀 1. Funcionalidades Principales

### 🔐 Autenticación de Personal
- **Pantalla de Login Inicial:** El sistema requiere que el personal se identifique.
- **Roles de Usuario:**
  - **Mesero:** Acceso a toma de comandas, monitor de mesas y gestión de menú.
  - **Cocinero:** Acceso especializado al Monitor de Cocina (KDS).
  - **Cajero:** Acceso a la sección de **Caja**, procesamiento de pagos y reportes.
  - **Administrador:** Acceso total, incluyendo configuración de tasas de cambio y gestión de personal.

### 📋 Gestión de Comandas (Mesas y Menú)
- **Mapa de Mesas:** Visualización en tiempo real del estado de cada mesa.
- **Toma de Pedidos:** Interfaz rápida para añadir platillos, con opción de **Notas Especiales** (ej: "Sin cebolla").
- **Tasa del Día:** Conversión dinámica USD/VES aplicada al menú y a la cuenta total.
- **Pre-cuenta:** Opción de imprimir un ticket de pre-cuenta antes de enviar a cocina.

### 🍳 Monitor de Cocina (KDS)
- **Flujo de Estados:** Gestión robusta del ciclo de vida del plato: `Pendiente` ➔ `Cocinando` ➔ `Listo`.
- **Notificaciones Real-time:** Al marcar un plato como "Listo para servir", el mesero recibe una notificación inmediata (visual y sonora).
- **Socket Stream:** Actualización automática sin necesidad de refrescar la pantalla gracias a la integración con Supabase Realtime.

### 💰 Caja y Facturación
- **Módulo de Caja:** Visible solo para roles autorizados.
- **Gestión de Pagos:** Lista de órdenes "Entregadas" listas para cobrar.
- **Conversión de Divisas:** Cálculo exacto en Bolívares (VES) basado en la tasa del día configurada.
- **Impresión de Tickets:** Integración con impresoras térmicas de 80mm para la emisión de facturas y tickets de despacho.
- **Exportación PDF:** Botón para generar reportes de caja en formato PDF (Tamaño Carta).

---

## 📱 2. Guía de Interfaz

### Barra de Navegación Inferior
1. **Inicio:** Acceso a la selección de mesas y toma de pedidos.
2. **Cocina:** Monitor KDS para ver el progreso de los platos.
3. **Historial:** Registro de órdenes pasadas y re-impresión de tickets.
4. **Caja (Solo Caja/Admin):** Procesamiento de pagos y cierre de cuentas.
5. **Perfil:** Configuración de la tasa del día, estado de conexión y cierre de sesión.

---

## ⚙️ 3. Configuración y Seguridad

### 💸 Tasa del Día
Configurable desde el menú de **Perfil**. Una vez guardada, se aplica instantáneamente a todo el sistema (Carta de precios y Box de pago).

### 📊 Auditoría y Reportes
- Los administradores pueden visualizar el panel de **Administración Financiera** en su perfil.
- **Exportación de Auditoría:** Generación de un reporte PDF detallado con todas las ventas del día, montos en USD/VES y estados de pedidos, optimizado para impresión en tamaño Carta.

### 🛡️ Seguridad (RLS)
Se han propuesto políticas de **Row Level Security (RLS)** en Supabase para:
- Restringir la lectura de `auditoria_financiera` solo a administradores.
- Impedir escrituras directas en tablas críticas desde usuarios no autenticados.

---

## 🖨️ 4. Sistema de Impresión
El sistema utiliza una ventana de impresión optimizada para **Google Cloud Print** o drivers locales de Android, generando un formato HTML limpio de 80mm que incluye:
- Nombre del Restaurante y Fecha.
- Detalle de ítems y cantidades.
- Totales en USD y VES.
- Espacio para firma o propina.

---

## 🛠 5. Arquitectura Técnica
- **Frontend:** Jetpack Compose (Kotlin).
- **Backend:** Supabase (Auth, Database, Realtime).
- **Persistencia Local:** Room Database y SharedPreferences para configuraciones rápidas.
- **Impresión:** WebView-based PrintManager.

---
*Documentación generada automáticamente por el Asistente de IA Studio.*
