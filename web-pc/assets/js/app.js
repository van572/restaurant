// CONTROLLER COMPLETO Y OPERATIVO DE COCINA, CAJA Y AUDITORÍA - RESTAURANTE MULTI-VIEW
// Implementa la lógica de cobros, actualización de pedidos, cálculo de KPI y exportación de logs financieros.

// ----------------------------------------------------
// 1. CONFIGURACIÓN COMPARTIDA Y MENÚ
// ----------------------------------------------------
const MENU_RESTAURANTE_DEFAULT = {
    "Hamburguesa Premium": 12.50,
    "Pizza Personal Pepperoni": 15.00,
    "Tacos de Res (x3)": 8.50,
    "Papas Fritas": 4.00,
    "Alitas BBQ": 9.50,
    "Té Frío Limón": 3.00,
    "Refresco Sabor Cola": 2.50,
    "Agua Mineral": 2.00
};

let MENU_RESTAURANTE = {};

function initMenuFromStorage() {
    const raw = localStorage.getItem('custom_menu_restaurante');
    if (raw) {
        try {
            MENU_RESTAURANTE = JSON.parse(raw);
        } catch(e) {
            MENU_RESTAURANTE = { ...MENU_RESTAURANTE_DEFAULT };
        }
    } else {
        MENU_RESTAURANTE = { ...MENU_RESTAURANTE_DEFAULT };
    }
}
initMenuFromStorage();

// Formateador de moneda para homogeneizar pesos/dólares
const formatCurrency = (monto) => {
    return new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(monto);
};

// ----------------------------------------------------
// 0. SISTEMA DE NOTIFICACIONES Y SONIDO (Web Audio API)
// ----------------------------------------------------
const AppNotifications = {
    audioCtx: null,
    audioBuffer: null,
    isInitialized: false,

    async initAudio() {
        if (this.isInitialized) return;
        
        try {
            this.audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            const response = await fetch('https://assets.mixkit.co/active_storage/sfx/2869/2869-preview.mp3');
            const arrayBuffer = await response.arrayBuffer();
            this.audioBuffer = await this.audioCtx.decodeAudioData(arrayBuffer);
            this.isInitialized = true;
            console.log("🔊 Web Audio API activada.");
            
            // Ocultar botón de activación si existe
            const btn = document.getElementById('btn-enable-audio');
            if (btn) btn.style.display = 'none';
            
            this.playAlert(); // Sonido de prueba
        } catch (e) {
            console.error("No se pudo iniciar el audio:", e);
        }
    },

    playAlert() {
        // Intentar beep sintetizado primero (más confiable y "atencional")
        this.playBeep(440, 0.15); // La natural
        setTimeout(() => this.playBeep(554.37, 0.2), 150); // Do sostenido (acorde mayor alegre)

        if (!this.isInitialized || !this.audioBuffer) {
            const audio = document.getElementById('notification-sound');
            if (audio) {
                audio.currentTime = 0;
                audio.play().catch(() => {});
            }
            return;
        }

        if (this.audioCtx.state === 'suspended') {
            this.audioCtx.resume();
        }

        const source = this.audioCtx.createBufferSource();
        source.buffer = this.audioBuffer;
        source.connect(this.audioCtx.destination);
        source.start(0);
    },

    playBeep(frecuencia, duracion) {
        if (!this.audioCtx) {
            try {
                this.audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            } catch(e) { return; }
        }
        
        if (this.audioCtx.state === 'suspended') {
            this.audioCtx.resume();
        }

        const oscillator = this.audioCtx.createOscillator();
        const gainNode = this.audioCtx.createGain();

        oscillator.connect(gainNode);
        gainNode.connect(this.audioCtx.destination);

        oscillator.type = 'sine';
        oscillator.frequency.value = frecuencia;
        
        gainNode.gain.setValueAtTime(0, this.audioCtx.currentTime);
        gainNode.gain.linearRampToValueAtTime(0.2, this.audioCtx.currentTime + 0.01);
        gainNode.gain.exponentialRampToValueAtTime(0.0001, this.audioCtx.currentTime + duracion);

        oscillator.start(this.audioCtx.currentTime);
        oscillator.stop(this.audioCtx.currentTime + duracion);
    },

    show(mensaje, tipo = 'info') {
        if (typeof Toast !== 'undefined') {
            if (tipo === 'alerta') Toast.warning("Alerta", mensaje);
            else if (tipo === 'error') Toast.error("Error", mensaje);
            else Toast.info("Notificación", mensaje);
            this.playAlert();
            return;
        }

        const container = document.getElementById('notification-container');
        if (!container) return;

        const notif = document.createElement('div');
        notif.className = 'notificacion-floating-card';
        if (tipo === 'alerta') notif.style.borderLeftColor = 'var(--color-danger)';
        
        const timestamp = new Date().toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
        
        notif.innerHTML = `
            <div class="notificacion-icon">${tipo === 'alerta' ? '🔔' : 'ℹ️'}</div>
            <div class="notificacion-body">
                <div class="notificacion-text">${mensaje}</div>
                <div class="notificacion-time">${timestamp}</div>
            </div>
        `;
        
        container.appendChild(notif);
        this.playAlert();

        setTimeout(() => {
            notif.style.animation = 'fadeOutRight 0.5s forwards';
            setTimeout(() => notif.remove(), 500);
        }, 5000);
    }
};

// ----------------------------------------------------
// 0.1 CONTROL DE ACCESO (LOGIN SENCILLO)
// ----------------------------------------------------
// --- REMOVED CONFLICTING AUTHMANAGER ---

// ----------------------------------------------------
// 2. CONTROLADOR DE COCINA (KDS - Kitchen Display System)
// ----------------------------------------------------
const CocinaController = {
    pedidos: [],
    realMenu: [],

    async init() {
        console.log("🥣 Cocina: Cargando órdenes activas...");
        await this.cargarMenu();
        await this.cargarYRenderizar();

        // Enlace en tiempo real de Supabase
        if (window.DataService) {
            window.onRealtimePedidosUpdate = async (payload) => {
                console.log("🍳 Cocina recibió notificación de cambio de datos.");
                if (payload.eventType === 'INSERT') {
                    AppNotifications.show(`¡Nuevo Pedido de la ${payload.new.mesa}!`, 'alerta');
                }
                await this.cargarYRenderizar();
            };
        }
    },

    async cargarMenu() {
        try {
            if (window.DataService) {
                this.realMenu = await DataService.fetchMenu();
                this.renderMenu();
            }
        } catch (e) {
            console.warn("No se pudo cargar menú real en Cocina, usando local storage.");
            this.renderMenu(); // Fallback to global constants if exists
        }
    },

    async cargarYRenderizar() {
        try {
            const todos = await DataService.fetchPedidos();
            this.pedidos = todos.filter(p => p.estado === 'pendiente' || p.estado === 'cocinando' || p.estado === 'listo');
            this.renderListas();
        } catch (e) {
            console.error("Error al cargar pedidos en Cocina:", e);
        }
    },

    renderMenu() {
        const grid = document.getElementById('menu-items-pricing');
        if (!grid) return;

        let itemsToRender = [];
        if (this.realMenu && this.realMenu.length > 0) {
            itemsToRender = this.realMenu.map(m => ({ nombre: m.nombre, precio: m.precio }));
        } else {
            itemsToRender = Object.entries(MENU_RESTAURANTE).map(([n, p]) => ({ nombre: n, precio: p }));
        }

        grid.innerHTML = itemsToRender
            .map(item => `
                <div class="menu-item-row">
                    <span class="menu-name">${item.nombre}</span>
                    <span class="menu-price">${formatCurrency(item.precio)}</span>
                </div>
            `).join('');
    },

    renderListas() {
        const colPendientes = document.getElementById('lista-pendientes');
        const colProgreso = document.getElementById('lista-progreso');
        const colListos = document.getElementById('lista-listos');

        if (!colPendientes || !colProgreso || !colListos) return;

        colPendientes.innerHTML = '';
        colProgreso.innerHTML = '';
        colListos.innerHTML = '';

        let cntPend = 0, cntProg = 0, cntList = 0;

        this.pedidos.forEach(pedido => {
            const card = this.crearCardPedido(pedido);
            if (pedido.estado === 'pendiente') { colPendientes.appendChild(card); cntPend++; }
            else if (pedido.estado === 'cocinando') { colProgreso.appendChild(card); cntProg++; }
            else if (pedido.estado === 'listo') { colListos.appendChild(card); cntList++; }
        });

        document.getElementById('badge-pendientes').textContent = cntPend;
        document.getElementById('badge-progreso').textContent = cntProg;
        document.getElementById('badge-listos').textContent = cntList;

        if (cntPend === 0) colPendientes.innerHTML = '<div class="alerta-vacia">Sin órdenes entrantes 🍃</div>';
        if (cntProg === 0) colProgreso.innerHTML = '<div class="alerta-vacia">Ningún platillo en cocción 🔥</div>';
        if (cntList === 0) colListos.innerHTML = '<div class="alerta-vacia">Sin órdenes listas para servir 🍽️</div>';
    },

    crearCardPedido(pedido) {
        const div = document.createElement('div');
        div.className = `kds-card border-${pedido.estado}`;
        div.setAttribute('data-id', pedido.id);
        const itemsHtml = pedido.items.map(item => `<div class="kds-item-line"><span class="qty">${item.cantidad}x</span><span class="prod">${item.producto}</span>${item.notas ? `<p class="notas-platillo">💡 *${item.notas}*</p>` : ''}</div>`).join('');
        const timestamp = pedido.creado_en || pedido.created_at || new Date().toISOString();
        const minutosTranscurridos = Math.floor((Date.now() - new Date(timestamp)) / 60000);
        let colorRetraso = minutosTranscurridos > 15 ? 'retraso-critico' : (minutosTranscurridos > 8 ? 'retraso-medio' : 'retraso-bajo');
        let botonAccion = '';
        if (pedido.estado === 'pendiente') botonAccion = `<button class="btn-kds-accion btn-empezar" onclick="CocinaController.cambiarEstado(${pedido.id}, 'cocinando')">👩‍🍳 Empezar</button>`;
        else if (pedido.estado === 'cocinando') botonAccion = `<button class="btn-kds-accion btn-completar" onclick="CocinaController.cambiarEstado(${pedido.id}, 'listo')">✅ Listo</button>`;
        else if (pedido.estado === 'listo') botonAccion = `<button class="btn-kds-accion btn-despachar" onclick="CocinaController.cambiarEstado(${pedido.id}, 'entregado')">🚀 Servir</button>`;

        div.innerHTML = `
            <div class="kds-card-header"><div><span class="mesa-badge">${pedido.mesa}</span></div><span class="cronometro ${colorRetraso}">${minutosTranscurridos} min</span></div>
            <div class="kds-card-body"><p class="mesero-tag">Mesero: ${pedido.mesero}</p><div class="kds-items-list">${itemsHtml}</div></div>
            <div class="kds-card-footer">${botonAccion}</div>
        `;
        return div;
    },

    async cambiarEstado(id, nuevoEstado) {
        try {
            await DataService.actualizarEstadoPedido(id, nuevoEstado);
            Toast.success("Estado Actualizado", `Pedido #${id} marcado como ${nuevoEstado}.`);
            await this.cargarYRenderizar();
        } catch (e) { Toast.error("Error", e.message); }
    }
}

// ----------------------------------------------------
// 3. CONTROLADOR DE CAJA (Billing & Checkout)
// ----------------------------------------------------
const CajaController = {
    pedidos: [],
    pedidoSeleccionado: null,

    async init() {
        console.log("💰 Caja: Cargando pedidos...");
        await this.cargarYRenderizar();
        window.onRealtimePedidosUpdate = async () => { await this.cargarYRenderizar(); };
    },

    async cargarYRenderizar() {
        try {
            const todos = await DataService.fetchPedidos();
            this.pedidos = todos.filter(p => p.estado !== 'pagado');
            this.renderListaMesas();
            if (this.pedidoSeleccionado) {
                const act = this.pedidos.find(p => p.id == this.pedidoSeleccionado.id);
                if (act) this.verDetalle(act.id); else this.cerrarDetalle();
            }
        } catch (e) { 
            console.error("Caja Error:", e);
            const cont = document.getElementById('grid-mesas-caja');
            if (cont) cont.innerHTML = `
                <div class="alerta-vacia total-ancho" style="border-color: var(--color-danger); background: rgba(239, 68, 68, 0.05);">
                    <h3 style="color: var(--color-danger); margin-bottom: 10px;">❌ Error de Conexión</h3>
                    <p style="color: white; margin-bottom: 5px;">${e.message || 'No se pudieron cargar los pedidos.'}</p>
                    <p style="font-size: 0.85rem; color: var(--color-text-muted);">Verifica que las tablas existan en Supabase ejecutando el script en <b>database/esquema.sql</b>.</p>
                </div>
            `;
        }
    },

    renderListaMesas() {
        const cont = document.getElementById('grid-mesas-caja');
        if (!cont) return;
        cont.innerHTML = '';
        if (this.pedidos.length === 0) {
            cont.innerHTML = '<div class="alerta-vacia total-ancho"><h3>No hay cuentas pendientes 🎉</h3></div>';
            return;
        }
        this.pedidos.forEach(p => {
            const isPagoMovilPendiente = p.metodo_pago === 'pago_movil' && p.estado_pago === 'pendiente';
            const div = document.createElement('div');
            div.className = `mesa-caja-card state-${p.estado} ${this.pedidoSeleccionado && this.pedidoSeleccionado.id === p.id ? 'mesa-activa' : ''} ${isPagoMovilPendiente ? 'pm-alert-card' : ''}`;
            div.onclick = () => this.verDetalle(p.id);
            div.innerHTML = `
                <div class="caja-mesa-header">
                    <span class="mesa-tag-number">${p.mesa}</span>
                    <span class="mesa-estado-pill border-${p.estado}">${p.estado.toUpperCase()}</span>
                </div>
                <div class="caja-mesa-body">
                    ${isPagoMovilPendiente ? '<div class="pm-badge-blink">⚠️ PAGO MÓVIL POR VERIFICAR</div>' : ''}
                    <p class="caja-mesa-mesero">Mesero: ${p.mesero}</p>
                    <p class="caja-mesa-monto">${formatCurrency(p.total)}</p>
                </div>
            `;
            cont.appendChild(div);
        });
    },

    verDetalle(id) {
        const p = this.pedidos.find(ped => ped.id == id);
        if (!p) return;
        this.pedidoSeleccionado = p;
        this.renderListaMesas();
        document.getElementById('detalle-cuenta-pane').style.display = 'flex';
        document.getElementById('select-cuenta-prompt').style.display = 'none';
        document.getElementById('cuenta-mesa-titulo').textContent = p.mesa;
        document.getElementById('cuenta-total-resumen').textContent = formatCurrency(p.total);
        document.getElementById('cuenta-items-list').innerHTML = p.items.map(it => `<div class="cuenta-item-line"><span>${it.cantidad}x ${it.producto}</span><span>${formatCurrency(it.precio * it.cantidad)}</span></div>`).join('');
        
        // Mostrar Datos de Pago Móvil si existen
        const areaPago = document.getElementById('pm-conciliacion-area');
        if (p.metodo_pago === 'pago_movil') {
            areaPago.style.display = 'block';
            areaPago.innerHTML = `
                <div class="pm-conciliacion-box">
                    <h4>🔍 Conciliación Pago Móvil</h4>
                    <div class="pm-data-grid">
                        <div><small>Banco:</small> <p>${p.pago_banco}</p></div>
                        <div><small>Teléfono:</small> <p>${p.pago_telefono}</p></div>
                        <div><small>Referencia:</small> <p class="ref-highlight">${p.pago_referencia}</p></div>
                    </div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 15px;">
                        <button class="btn-approve" onclick="CajaController.validarPago(${p.id}, 'aprobado')">
                            <ion-icon name="checkmark-circle"></ion-icon> Aprobar Pago
                        </button>
                        <button class="btn-reject" onclick="CajaController.validarPago(${p.id}, 'rechazado')">
                            <ion-icon name="close-circle"></ion-icon> Rechazar
                        </button>
                    </div>
                </div>
            `;
        } else {
            areaPago.style.display = 'none';
        }

        this.calcularSugerenciasCambio(p.total);
    },

    async validarPago(id, decision) {
        if (!confirm(`¿Estás seguro de ${decision} este pago?`)) return;
        try {
            await DataService.actualizarEstadoPago(id, decision);
            if (decision === 'aprobado') {
                const p = this.pedidos.find(ped => ped.id == id);
                // Si aprobamos el pago, procedemos al cobro definitivo
                await DataService.cobrarsePedido(id, p.total, 'pago_movil', p.mesero);
                Toast.success("Pago Aprobado", "Venta registrada exitosamente.");
            } else {
                Toast.warning("Pago Rechazado", "Se ha notificado al cliente.");
            }
            this.cerrarDetalle();
            await this.cargarYRenderizar();
        } catch (e) {
            Toast.error("Error", e.message);
        }
    },

    cerrarDetalle() {
        this.pedidoSeleccionado = null;
        document.getElementById('detalle-cuenta-pane').style.display = 'none';
        document.getElementById('select-cuenta-prompt').style.display = 'flex';
        this.renderListaMesas();
    },

    calcularSugerenciasCambio(total) {
        const inp = document.getElementById('caja-recibido-input');
        if (inp) { inp.value = ''; inp.setAttribute('min', total); }
        document.getElementById('caja-cambio-calculado').textContent = formatCurrency(0);
    },

    rellenarMontoEfectivo(monto) {
        const inp = document.getElementById('caja-recibido-input');
        if (inp) { inp.value = monto.toFixed(2); this.recalcularCambio(); }
    },

    recalcularCambio() {
        if (!this.pedidoSeleccionado) return;
        const total = this.pedidoSeleccionado.total;
        const cobro = parseFloat(document.getElementById('caja-recibido-input').value) || 0;
        const span = document.getElementById('caja-cambio-calculado');
        if (cobro >= total) {
            span.textContent = formatCurrency(cobro - total);
            span.style.color = '#10B981';
        } else {
            span.textContent = 'Monto insuficiente';
            span.style.color = '#EF4444';
        }
    },

    async ejecutarCobro(m) {
        if (!this.pedidoSeleccionado) return;
        const total = this.pedidoSeleccionado.total;
        if (m === 'efectivo' && (parseFloat(document.getElementById('caja-recibido-input').value) || 0) < total) { 
            Toast.error("Error de Cobro", "Monto insuficiente para cubrir el total."); 
            return; 
        }
        if (!confirm("¿Confirmar cobro de " + formatCurrency(total) + "?")) return;
        try {
            await DataService.cobrarsePedido(this.pedidoSeleccionado.id, total, m, this.pedidoSeleccionado.mesero);
            Toast.success("Venta Exitosa", "La cuenta ha sido cerrada y pagada.");
            this.cerrarDetalle();
            await this.cargarYRenderizar();
        } catch (e) { Toast.error("Error", e.message); }
    }
};

// ----------------------------------------------------
// 4. CONTROLADOR DE AUDITORÍA (Dashboards & Reporting)
// ----------------------------------------------------
const AuditoriaController = {
    logs: [],
    charts: {},

    async init() {
        await this.cargarYVisualizar();
        window.onRealtimeAuditoriaUpdate = async () => { await this.cargarYVisualizar(); };
    },

    async cargarYVisualizar() {
        try {
            this.logs = await DataService.fetchAuditoria();
            this.calcularKPIs();
            this.renderLogsTable();
            this.renderCharts();
        } catch (e) { console.error(e); }
    },

    calcularKPIs() {
        const total = this.logs.reduce((acc, l) => acc + parseFloat(l.monto), 0);
        document.getElementById('kpi-ventas-totales').textContent = formatCurrency(total);
        document.getElementById('kpi-transacciones-recuento').textContent = this.logs.length;
    },

    renderCharts() {
        const lineCtx = document.getElementById('chart-ventas-linea');
        const pieCtx = document.getElementById('chart-pie-categorias');
        if (!lineCtx || !pieCtx) return;

        if (this.charts.line) this.charts.line.destroy();
        if (this.charts.pie) this.charts.pie.destroy();

        const shortLogs = this.logs.slice(0, 10).reverse();
        this.charts.line = new Chart(lineCtx, {
            type: 'line',
            data: {
                labels: shortLogs.map(l => new Date(l.creado_en).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })),
                datasets: [{ label: 'Ventas', data: shortLogs.map(l => l.monto), borderColor: '#10B981', tension: 0.3 }]
            }
        });

        const metodos = this.logs.map(l => l.metodo_pago);
        const freq = metodos.reduce((a, v) => (a[v] = (a[v] || 0) + 1, a), {});
        this.charts.pie = new Chart(pieCtx, {
            type: 'doughnut',
            data: {
                labels: Object.keys(freq).map(k => k.toUpperCase()),
                datasets: [{ data: Object.values(freq), backgroundColor: ['#10B981', '#3B82F6', '#8B5CF6'] }]
            }
        });
    },

    renderLogsTable() {
        const tbody = document.getElementById('tbody-auditoria-logs');
        if (!tbody) return;
        tbody.innerHTML = this.logs.map(log => `
            <tr>
                <td>#${log.id}</td>
                <td>Pedido #${log.pedido_id}</td>
                <td>${log.metodo_pago.toUpperCase()}</td>
                <td>${log.mesero}</td>
                <td style="color:#10B981; font-weight:700;">${formatCurrency(log.monto)}</td>
                <td>${new Date(log.creado_en).toLocaleTimeString()}</td>
            </tr>
        `).join('');
    },

    filtrarYBuscar() { /* logic here if needed */ },
    limpiarFiltros() { /* logic here if needed */ },
    exportarCSV() { /* logic here if needed */ }
};

// ----------------------------------------------------
// 5. CONFIGURACIÓN MODAL DE CONEXIÓN CON SUPABASE
// ----------------------------------------------------
const SettingsModal = {
    abrir() {
        let el = document.getElementById('settings-supabase-modal');
        if (!el) el = this.crearModalDom();
        el.style.display = 'flex';
        const config = DataService.getConfig();
        document.getElementById('input-set-url').value = config.url || '';
        document.getElementById('input-set-key').value = config.anonKey || '';
    },
    cerrar() { document.getElementById('settings-supabase-modal').style.display = 'none'; },
    guardar() {
        const url = document.getElementById('input-set-url').value.trim();
        const key = document.getElementById('input-set-key').value.trim();
        DataService.saveConfig(url, key);
        alert("Reiniciando...");
        location.reload();
    },
    desvincular() { if (confirm("Eliminar?")) { DataService.resetConfig(); location.reload(); } },
    crearModalDom() {
        const modal = document.createElement('div');
        modal.id = 'settings-supabase-modal';
        modal.className = 'supabase-config-modal-overlay';
        modal.innerHTML = `
            <div class="supabase-config-modal-card">
              <h3>⚙️ Configuración Supabase</h3>
              <input type="text" id="input-set-url" placeholder="URL" />
              <textarea id="input-set-key" placeholder="Anon Key"></textarea>
              <button onclick="SettingsModal.guardar()">Guardar</button>
              <button onclick="SettingsModal.cerrar()">Cerrar</button>
            </div>
        `;
        document.body.appendChild(modal);
        return modal;
    }
};

// ----------------------------------------------------
// 7. SOLICITUDES DE SERVICIO
// ----------------------------------------------------
const SolicitudesController = {
    async init() {
        await this.cargar();
        DataService.suscribirASolicitudes(async (p) => {
            if (p.eventType === 'INSERT') AppNotifications.show(`${p.new.mesa} solicita asistencia`, 'alerta');
            await this.cargar();
        });
    },
    async cargar() {
        const list = await DataService.fetchSolicitudes();
        const cont = document.getElementById('solicitudes-container');
        if (cont) cont.innerHTML = list.map(s => `<div style="background:var(--color-bg-surface); padding:10px; border-radius:8px; margin-bottom:5px; display:flex; justify-content:space-between;"><span>${s.mesa}: ${s.tipo}</span><button onclick="SolicitudesController.atender(${s.id})">OK</button></div>`).join('');
    },
    async atender(id) { await DataService.atenderSolicitud(id); await this.cargar(); }
};

// ----------------------------------------------------
// 8. ADMIN MENÚ
// ----------------------------------------------------
const AdminMenuController = {
    menu: [],
    async init() {
        await this.cargar();
        // Suscribirse a cambios en tiempo real del menú si está disponible
        if (DataService.suscribirAMenu) {
            DataService.suscribirAMenu(async () => {
                await this.cargar();
            });
        }
    },
    async cargar() {
        try {
            this.menu = await DataService.fetchMenu();
            this.render();
        } catch (e) {
            console.error("Error al cargar menú en Admin:", e);
        }
    },
    render() {
        const cont = document.getElementById('admin-menu-list');
        if (!cont) return;

        if (this.menu.length === 0) {
            cont.innerHTML = `
                <div class="alerta-vacia total-ancho">
                    <h3>No hay platillos en el menú 🍽️</h3>
                    <p>Agrega el primer platillo usando el botón superior.</p>
                </div>
            `;
            return;
        }

        cont.innerHTML = this.menu.sort((a, b) => a.categoria.localeCompare(b.categoria)).map(m => `
            <div class="mesa-caja-card" style="cursor: default; border-color: ${m.disponible ? 'var(--color-border)' : 'var(--color-danger)'}; opacity: ${m.disponible ? 1 : 0.7};">
                <div class="caja-mesa-header">
                    <span class="mesa-tag-number" style="background: var(--color-bg-surface);">${m.emoji || '🍴'} ${m.categoria}</span>
                    <span class="mesa-estado-pill ${m.disponible ? 'border-listo' : 'border-pendiente'}">
                        ${m.disponible ? 'ACTIVO' : 'SIN STOCK'}
                    </span>
                </div>
                <div class="caja-mesa-body">
                    <h3 style="font-size: 1.1rem; margin-top: 5px;">${m.nombre}</h3>
                    <p class="caja-mesa-mesero">${m.descripcion || 'Sin descripción'}</p>
                    <p class="caja-mesa-monto" style="margin-top: 10px;">${formatCurrency(m.precio)}</p>
                </div>
                <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; margin-top: 15px;">
                    <button onclick="AdminMenuController.toggle(${m.id}, ${m.disponible})" class="btn-checkout" style="background: ${m.disponible ? 'var(--color-accent)' : 'var(--color-primary)'}; padding: 6px; font-size: 0.75rem;">
                        <span>${m.disponible ? '🚫' : '✅'}</span> ${m.disponible ? 'Pausar' : 'Activar'}
                    </button>
                    <button onclick="AdminMenuController.editar(${m.id})" class="btn-checkout checkout-tarjeta" style="padding: 6px; font-size: 0.75rem;">
                        <span>✏️</span> Editar
                    </button>
                    <button onclick="AdminMenuController.borrar(${m.id})" class="btn-checkout" style="background: var(--color-danger); padding: 6px; font-size: 0.75rem;">
                        <span>🗑️</span> Borrar
                    </button>
                </div>
            </div>
        `).join('');
    },
    async toggle(id, d) {
        try {
            await DataService.actualizarDisponibilidadMenu(id, !d);
            Toast.success("Menú Actualizado", `Disponibilidad cambiada correctamente.`);
            await this.cargar();
        } catch (e) { Toast.error("Error", e.message); }
    },
    async agregarNuevo() {
        this.mostrarFormulario();
    },
    async editar(id) {
        const item = this.menu.find(m => m.id === id);
        if (item) this.mostrarFormulario(item);
    },
    async borrar(id) {
        if (!confirm("¿Seguro que deseas eliminar este platillo del menú?")) return;
        try {
            await DataService.eliminarItemMenu(id);
            Toast.success("Eliminado", "Platillo removido del menú.");
            await this.cargar();
        } catch (e) { Toast.error("Error", e.message); }
    },
    mostrarFormulario(item = null) {
        const isEditing = !!item;

        // Limpiar cualquier modal previo
        let existing = document.getElementById('menu-item-editor-modal');
        if (existing) existing.remove();

        const modal = document.createElement('div');
        modal.id = 'menu-item-editor-modal';
        modal.className = 'supabase-config-modal-overlay';
        modal.style.display = 'flex';

        const currentEmoji = item ? (item.emoji || '🍔') : '🍔';
        const currentCategory = item ? (item.categoria || 'COMIDA') : 'COMIDA';
        const isPeso = item && item.unidad_medida && item.unidad_medida !== 'unid';

        const emojisList = ["🍔", "🍕", "🌮", "🥗", "🥪", "🍰", "🍵", "🍳", "🍗", "🍟", "🥣", "🍦", "🍹", "🥩", "🍷", "🍺", "☕", "🥞", "🍝", "🍩", "🧁", "🍪", "🥤", "🍣", "🍛", "🥫"];
        const categoriesList = ["COMIDA", "BEBIDA", "ACOMPAÑAMIENTO", "POSTRE", "OTROS"];

        let emojiChipsHtml = emojisList.map(e => `
            <span class="menu-emoji-chip ${e === currentEmoji ? 'selected' : ''}" data-emoji="${e}" onclick="document.querySelectorAll('.menu-emoji-chip').forEach(c=>c.classList.remove('selected')); this.classList.add('selected'); document.getElementById('editor-emoji-display').innerText='${e}';">${e}</span>
        `).join('');

        let categoryChipsHtml = categoriesList.map(cat => `
            <span class="menu-category-chip ${cat === currentCategory.toUpperCase() ? 'selected' : ''}" data-category="${cat}" onclick="document.querySelectorAll('.menu-category-chip').forEach(c=>c.classList.remove('selected')); this.classList.add('selected'); document.getElementById('editor-categoria').value='${cat}';">${cat}</span>
        `).join('');

        modal.innerHTML = `
            <div class="supabase-config-modal-card" style="width: 500px; max-height: 90vh; display: flex; flex-direction: column;">
                <div class="modal-card-header">
                    <h3 style="display: flex; align-items: center; gap: 8px;">
                        <span>${isEditing ? '✏️' : '✨'}</span>
                        ${isEditing ? 'Editar Producto' : 'Nuevo Producto en el Menú'}
                    </h3>
                    <button class="btn-close-modal" onclick="document.getElementById('menu-item-editor-modal').remove()">&times;</button>
                </div>
                <div class="modal-card-body" style="overflow-y: auto; flex: 1;">
                    <!-- card ID 1: Identidad visual -->
                    <div class="visual-card-section">
                        <span class="visual-card-title">Identidad del Producto</span>
                        <div style="display: flex; align-items: center; gap: 16px;">
                            <div class="emoji-display-box" id="editor-emoji-display" title="Emoji del producto">${currentEmoji}</div>
                            <div class="form-group-modal" style="flex: 1; gap: 4px;">
                                <label>Nombre del Producto</label>
                                <input type="text" id="editor-nombre" value="${item ? item.nombre : ''}" placeholder="Ej: Pizza Súper Suprema" style="font-family: inherit; font-size: 0.9rem;" required />
                            </div>
                        </div>
                        <span style="font-size: 0.72rem; font-weight: 700; color: var(--color-text-muted); margin-top: 4px;">Selecciona un Símbolo</span>
                        <div class="menu-emoji-grid">
                            ${emojiChipsHtml}
                        </div>
                    </div>

                    <!-- card ID 2: Comercial -->
                    <div class="visual-card-section">
                        <span class="visual-card-title">Precio y Categoría</span>
                        <div class="form-group-modal" style="gap: 4px;">
                            <label>Precio de Venta ($)</label>
                            <input type="number" id="editor-precio" value="${item ? item.precio : ''}" placeholder="0.00" step="0.01" style="font-family: inherit; font-size: 0.9rem;" required />
                        </div>
                        <div class="form-group-modal" style="gap: 4px; margin-top: 4px;">
                            <label>Categoría</label>
                            <input type="text" id="editor-categoria" value="${currentCategory}" placeholder="COMIDA" style="font-family: inherit; font-size: 0.9rem; text-transform: uppercase; margin-bottom: 6px;" required />
                            <div class="menu-category-chips">
                                ${categoryChipsHtml}
                            </div>
                        </div>
                    </div>

                    <!-- card ID 3: Configuración avanzada -->
                    <div class="visual-card-section">
                        <span class="visual-card-title">Detalles Operativos</span>
                        <div class="form-group-modal" style="gap: 4px;">
                            <label>Descripción / Fórmula</label>
                            <textarea id="editor-descripcion" placeholder="Notas de ingredientes o preparación..." style="font-family: inherit; font-size: 0.85rem; resize: vertical; min-height: 48px;">${item ? (item.descripcion || '') : ''}</textarea>
                        </div>
                        <div class="switch-container ${isPeso ? 'active' : ''}" style="margin-top: 4px;" id="editor-peso-container">
                            <input type="checkbox" id="editor-es-peso" style="cursor: pointer; width: 16px; height: 16px;" ${isPeso ? 'checked' : ''} />
                            <div style="display: flex; flex-direction: column;">
                                <span style="font-size: 0.82rem; font-weight: 700; color: var(--color-text-main);">Se vende por PESO</span>
                                <span style="font-size: 0.7rem; color: var(--color-text-muted);">Habilita el ingreso de peso exacto (Kg) al vender</span>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal-card-footer">
                    <button class="btn-modal-action btn-secondary-modal" onclick="document.getElementById('menu-item-editor-modal').remove()">Cancelar</button>
                    <button class="btn-modal-action btn-success-modal" id="btn-save-menu-item" style="color: #1a1a1a;">ACEPTAR Y GUARDAR</button>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        // Configurar toggle de peso
        const pesoContainer = document.getElementById('editor-peso-container');
        const esPesoInput = document.getElementById('editor-es-peso');

        const updatePesoClass = () => {
            if (esPesoInput.checked) {
                pesoContainer.classList.add('active');
            } else {
                pesoContainer.classList.remove('active');
            }
        };

        pesoContainer.addEventListener('click', (e) => {
            if (e.target !== esPesoInput) {
                esPesoInput.checked = !esPesoInput.checked;
            }
            updatePesoClass();
        });

        esPesoInput.addEventListener('change', () => {
            updatePesoClass();
        });

        // Configurar guardado
        document.getElementById('btn-save-menu-item').addEventListener('click', async () => {
            const nombreVal = document.getElementById('editor-nombre').value.trim();
            const precioVal = parseFloat(document.getElementById('editor-precio').value);
            const categoriaVal = document.getElementById('editor-categoria').value.trim().toUpperCase();
            const descripcionVal = document.getElementById('editor-descripcion').value.trim();

            const selectedEmojiChip = document.querySelector('.menu-emoji-chip.selected');
            const emojiVal = selectedEmojiChip ? selectedEmojiChip.getAttribute('data-emoji') : '🍔';

            const isPesoVal = esPesoInput.checked;

            if (!nombreVal) {
                return Toast.error("Faltan datos", "El nombre es obligatorio");
            }
            if (isNaN(precioVal) || precioVal < 0) {
                return Toast.error("Error de Formato", "El precio de venta debe ser un número positivo");
            }
            if (!categoriaVal) {
                return Toast.error("Faltan datos", "La categoría es obligatoria");
            }

            const nuevoItem = {
                id: item ? item.id : undefined,
                nombre: nombreVal,
                precio: precioVal,
                categoria: categoriaVal,
                descripcion: descripcionVal,
                emoji: emojiVal,
                unidad_medida: isPesoVal ? 'kg' : 'unid',
                disponible: item ? item.disponible : true
            };

            document.getElementById('menu-item-editor-modal').remove();
            await AdminMenuController.guardar(nuevoItem);
        });
    },
    async guardar(item) {
        try {
            await DataService.guardarItemMenu(item);
            Toast.success("Guardado", "Platillo actualizado en el menú.");
            await this.cargar();
        } catch (e) {
            Toast.error("Error al guardar", e.message);
        }
    }
};

const MenuEditor = {
    async abrirModal() {
        if (window.AdminMenuController) {
            await AdminMenuController.agregarNuevo();
        } else {
            Toast.error("Error", "Controlador de menú no disponible.");
        }
    }
};

// Global bindings
window.MenuEditor = MenuEditor;
window.CocinaController = CocinaController;
window.CajaController = CajaController;
window.AuditoriaController = AuditoriaController;
window.SettingsModal = SettingsModal;
window.SolicitudesController = SolicitudesController;
window.AdminMenuController = AdminMenuController;
// window.AuthManager = AuthManager;
window.AppNotifications = AppNotifications;

document.addEventListener('DOMContentLoaded', () => {
    // WaiterSession.init(); // Optional simplified session
});
