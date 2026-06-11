// LISTENERS EN TIEMPO REAL PARA INTEGRACIÓN DE EVENTOS EN WEB-PC
// Suscribe las vistas a notificaciones reactivas de inserción o actualización de la base de datos de pedidos y transacciones.

document.addEventListener('DOMContentLoaded', () => {
    console.log("⚡ Inicializando receptores WebSocket para sincronización en tiempo real...");

    // 1. Suscripción a cambios de pedidos (Creado, Editado, Cambiado de Estado)
    const desuscribirPedidos = DataService.suscribirAPedidos((payload) => {
        console.log("🔔 [Realtime Event] Modificación de pedido detectada:", payload);
        
        // Lanzar una notificación visual flotante en el navegador
        mostrarNotificacionFlotante("Actualización en Pedidos recibida en tiempo real");

        // Llamar a los repintados reactivos de la vista activa si están registrados en el objeto global windows
        if (typeof window.onRealtimePedidosUpdate === 'function') {
            window.onRealtimePedidosUpdate(payload);
        }
    });

    // 2. Suscripción a registros de auditoría financiera
    const desuscribirAuditoria = DataService.suscribirAAuditoria((payload) => {
        console.log("🔔 [Realtime Event] Nuevo log de caja detectado:", payload);
        
        mostrarNotificacionFlotante("Nuevo ingreso registrado en caja");

        if (typeof window.onRealtimeAuditoriaUpdate === 'function') {
            window.onRealtimeAuditoriaUpdate(payload);
        }
    });

    // Manejar limpieza cuando se descargue la página para liberar hilos y sockets
    window.addEventListener('beforeunload', () => {
        if (typeof desuscribirPedidos === 'function') desuscribirPedidos();
        if (typeof desuscribirAuditoria === 'function') desuscribirAuditoria();
    });
});

// Función auxiliar de ayuda guiada para notificar sutilmente al operador de caja o cocina
function mostrarNotificacionFlotante(mensaje) {
    const contenedor = document.getElementById('notificaciones-realtime-container') || crearContenedorNotificaciones();
    
    const banner = document.createElement('div');
    banner.className = 'notificacion-floating-card';
    banner.innerHTML = `
        <div class="notificacion-icon">🔔</div>
        <div class="notificacion-body">
            <span class="notificacion-text">${mensaje}</span>
            <span class="notificacion-time">Ahora mismo</span>
        </div>
    `;
    
    contenedor.appendChild(banner);
    
    // No reproducimos sonido aquí para evitar duplicados, 
    // ya que AppNotifications.show() se encarga de ello en las vistas específicas.
    // reproducirTonoAccion();

    // Remover automáticamente tras 4 segundos con una transición visual agradable
    setTimeout(() => {
        banner.style.animation = 'fadeOutRight 0.5s forwards';
        setTimeout(() => banner.remove(), 500);
    }, 4500);
}

function crearContenedorNotificaciones() {
    const div = document.createElement('div');
    div.id = 'notificaciones-realtime-container';
    div.style.position = 'fixed';
    div.style.bottom = '20px';
    div.style.right = '20px';
    div.style.zIndex = '9999';
    div.style.display = 'flex';
    div.style.flexDirection = 'column';
    div.style.gap = '10px';
    div.style.pointerEvents = 'none';
    document.body.appendChild(div);
    return div;
}

// Fin de lógica de notificaciones
});
