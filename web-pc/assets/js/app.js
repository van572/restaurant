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
// 2. CONTROLADOR DE COCINA (KDS - Kitchen Display System)
// ----------------------------------------------------
const CocinaController = {
    pedidos: [],

    async init() {
        console.log("🥣 Cocina: Cargando órdenes activas...");
        this.renderMenu();
        await this.cargarYRenderizar();

        // Enlace en tiempo real de Supabase
        window.onRealtimePedidosUpdate = async (payload) => {
            console.log("🍳 Cocina recibió notificación de cambio de datos.");
            await this.cargarYRenderizar();
        };
    },

    async cargarYRenderizar() {
        try {
            // Cargar e intermediar pedidos que no estén cobrados (pagados) ni entregados aún
            const todos = await DataService.fetchPedidos();
            // Filtrar para visualización exclusiva en cocina (no queremos los ya finalizados 'entregado/pagado')
            this.pedidos = todos.filter(p => p.estado === 'pendiente' || p.estado === 'cocinando' || p.estado === 'listo');
            this.renderListas();
        } catch (e) {
            console.error("Error al cargar pedidos en Cocina:", e);
        }
    },

    renderMenu() {
        const grid = document.getElementById('menu-items-pricing');
        if (!grid) return;
        grid.innerHTML = Object.entries(MENU_RESTAURANTE)
            .map(([platillo, precio]) => `
                <div class="menu-item-row">
                    <span class="menu-name">${platillo}</span>
                    <span class="menu-price">${formatCurrency(precio)}</span>
                </div>
            `).join('');
    },

    renderListas() {
        const colPendientes = document.getElementById('lista-pendientes');
        const colProgreso = document.getElementById('lista-progreso');
        const colListos = document.getElementById('lista-listos');

        if (!colPendientes || !colProgreso || !colListos) return;

        // Limpiar
        colPendientes.innerHTML = '';
        colProgreso.innerHTML = '';
        colListos.innerHTML = '';

        // Contadores
        let cntPend = 0, cntProg = 0, cntList = 0;

        this.pedidos.forEach(pedido => {
            const card = this.crearCardPedido(pedido);
            
            if (pedido.estado === 'pendiente') {
                colPendientes.appendChild(card);
                cntPend++;
            } else if (pedido.estado === 'cocinando') {
                colProgreso.appendChild(card);
                cntProg++;
            } else if (pedido.estado === 'listo') {
                colListos.appendChild(card);
                cntList++;
            }
        });

        // Actualizar badges
        document.getElementById('badge-pendientes').textContent = cntPend;
        document.getElementById('badge-progreso').textContent = cntProg;
        document.getElementById('badge-listos').textContent = cntList;

        // Validar layouts vacíos y renderizar sugerencias
        if (cntPend === 0) colPendientes.innerHTML = '<div class="alerta-vacia">Sin órdenes entrantes 🍃</div>';
        if (cntProg === 0) colProgreso.innerHTML = '<div class="alerta-vacia">Ningún platillo en cocción 🔥</div>';
        if (cntList === 0) colListos.innerHTML = '<div class="alerta-vacia">Sin órdenes listas para servir 🍽️</div>';
    },

    crearCardPedido(pedido) {
        const div = document.createElement('div');
        div.className = `kds-card border-${pedido.estado}`;
        div.setAttribute('data-id', pedido.id);

        const itemsHtml = pedido.items.map(item => `
            <div class="kds-item-line">
                <span class="qty">${item.cantidad}x</span>
                <span class="prod">${item.producto}</span>
                ${item.notas ? `<p class="notas-platillo">💡 *${item.notas}*</p>` : ''}
            </div>
        `).join('');

        // Calcular minutos de retraso
        const timestamp = pedido.creado_en || pedido.created_at || new Date().toISOString();
        const tiempoCreado = new Date(timestamp);
        const minutosTranscurridos = Math.floor((Date.now() - tiempoCreado) / 60000);
        let colorRetraso = minutosTranscurridos > 15 ? 'retraso-critico' : (minutosTranscurridos > 8 ? 'retraso-medio' : 'retraso-bajo');

        // Botones de acción dinámicos basados en la fase del Kanban
        let botonAccion = '';
        if (pedido.estado === 'pendiente') {
            botonAccion = `<button class="btn-kds-accion btn-empezar" onclick="CocinaController.cambiarEstado(${pedido.id}, 'cocinando')">👩‍🍳 Empezar a Cocinar</button>`;
        } else if (pedido.estado === 'cocinando') {
            botonAccion = `<button class="btn-kds-accion btn-completar" onclick="CocinaController.cambiarEstado(${pedido.id}, 'listo')">✅ Marcar Listo</button>`;
        } else if (pedido.estado === 'listo') {
            botonAccion = `<button class="btn-kds-accion btn-despachar" onclick="CocinaController.cambiarEstado(${pedido.id}, 'entregado')">🚀 Servir / Despachar</button>`;
        }

        div.innerHTML = `
            <div class="kds-card-header">
                <div>
                    <span class="mesa-badge">${pedido.mesa}</span>
                    <span class="pedido-id-text">ID: #${pedido.id}</span>
                </div>
                <span class="cronometro ${colorRetraso}">${minutosTranscurridos} min</span>
            </div>
            <div class="kds-card-body">
                <p class="mesero-tag">Mesero: <strong>${pedido.mesero}</strong></p>
                <div class="kds-items-list">
                    ${itemsHtml}
                </div>
            </div>
            <div class="kds-card-footer">
                ${botonAccion}
            </div>
        `;
        return div;
    },

    async cambiarEstado(id, nuevoEstado) {
        try {
            const btn = document.querySelector(`[data-id="${id}"] .btn-kds-accion`);
            if (btn) btn.disabled = true;
            
            await DataService.actualizarEstadoPedido(id, nuevoEstado);
            await this.cargarYRenderizar();
        } catch (e) {
            alert("Error al actualizar la orden: " + e.message);
        }
    }
};

// ----------------------------------------------------
// 3. CONTROLADOR DE CAJA (Billing & Checkout)
// ----------------------------------------------------
const CajaController = {
    pedidos: [],
    pedidoSeleccionado: null,

    async init() {
        console.log("💰 Caja: Cargando pedidos pendientes de pago...");
        await this.cargarYRenderizar();

        // Enlace reactivo
        window.onRealtimePedidosUpdate = async (payload) => {
            console.log("Caja recibió notificación de cambio de datos.");
            await this.cargarYRenderizar();
        };
    },

    async cargarYRenderizar() {
        try {
            const todos = await DataService.fetchPedidos();
            // Mostrar todos los no pagados
            this.pedidos = todos.filter(p => p.estado !== 'pagado');
            this.renderListaMesas();
            
            if (this.pedidoSeleccionado) {
                // Actualizar detalle de la mesa si ya estaba abierta
                const actualizado = this.pedidos.find(p => p.id == this.pedidoSeleccionado.id);
                if (actualizado) {
                    this.verDetalle(actualizado.id);
                } else {
                    this.cerrarDetalle();
                }
            }
        } catch (e) {
            console.error("Error al refrescar la caja:", e);
        }
    },

    renderListaMesas() {
        const contenedor = document.getElementById('grid-mesas-caja');
        if (!contenedor) return;

        contenedor.innerHTML = '';

        if (this.pedidos.length === 0) {
            contenedor.innerHTML = `
                <div class="alerta-vacia total-ancho">
                    <h3>No hay mesas con cuentas pendientes 🎉</h3>
                    <p>Todos los pedidos del restaurante están al día y cobrados.</p>
                </div>
            `;
            return;
        }

        this.pedidos.forEach(pedido => {
            const div = document.createElement('div');
            // Distinguir mesa si tiene platillo listo por servir o cocinándose
            const activoClass = this.pedidoSeleccionado && this.pedidoSeleccionado.id === pedido.id ? 'mesa-activa' : '';
            div.className = `mesa-caja-card state-${pedido.estado} ${activoClass}`;
            div.onclick = () => this.verDetalle(pedido.id);

            // Iconito informativo del estado
            let statusIcon = '📝';
            if (pedido.estado === 'cocinando') statusIcon = '🔥';
            if (pedido.estado === 'listo') statusIcon = '🍽️';
            if (pedido.estado === 'entregado') statusIcon = '🚚';

            div.innerHTML = `
                <div class="caja-mesa-header">
                    <span class="mesa-tag-number">${pedido.mesa}</span>
                    <span class="mesa-estado-pill border-${pedido.estado}">${statusIcon} ${pedido.estado.toUpperCase()}</span>
                </div>
                <div class="caja-mesa-body">
                    <p class="caja-mesa-mesero">Mesero: ${pedido.mesero}</p>
                    <p class="caja-mesa-monto">${formatCurrency(pedido.total)}</p>
                </div>
            `;
            contenedor.appendChild(div);
        });
    },

    verDetalle(id) {
        const pedido = this.pedidos.find(p => p.id == id);
        if (!pedido) return;

        this.pedidoSeleccionado = pedido;
        
        // Re-dibujar selección en el listado lateral
        this.renderListaMesas();

        // Mostrar zona de detalle
        const pane = document.getElementById('detalle-cuenta-pane');
        const selectPrompt = document.getElementById('select-cuenta-prompt');
        if (pane) pane.style.display = 'block';
        if (selectPrompt) selectPrompt.style.display = 'none';

        // Llenar contenido
        document.getElementById('cuenta-mesa-titulo').textContent = `${pedido.mesa}`;
        document.getElementById('cuenta-pedido-id').textContent = `#${pedido.id}`;
        document.getElementById('cuenta-mesero-nombre').textContent = pedido.mesero;
        document.getElementById('cuenta-total-resumen').textContent = formatCurrency(pedido.total);
        
        // Items list
        const itemsList = document.getElementById('cuenta-items-list');
        itemsList.innerHTML = pedido.items.map(it => `
            <div class="cuenta-item-line">
                <div class="cuenta-item-izq">
                    <span class="cant">${it.cantidad}x</span>
                    <span class="name">${it.producto}</span>
                    ${it.notas ? `<p class="nota">*${it.notas}*</p>` : ''}
                </div>
                <span class="cuenta-item-precio">${formatCurrency(it.precio * it.cantidad)}</span>
            </div>
        `).join('');

        // Habilitar botón de cobro e inicializar sugerencias de efectivo
        this.calcularSugerenciasCambio(pedido.total);
    },

    cerrarDetalle() {
        this.pedidoSeleccionado = null;
        const pane = document.getElementById('detalle-cuenta-pane');
        const selectPrompt = document.getElementById('select-cuenta-prompt');
        if (pane) pane.style.display = 'none';
        if (selectPrompt) selectPrompt.style.display = 'block';
        this.renderListaMesas();
    },

    calcularSugerenciasCambio(total) {
        const sugerenciasList = document.getElementById('caja-sugerencias-efectivo');
        if (!sugerenciasList) return;

        // Generar montos redondos de sugerencia superiores al total
        const billetes = [20, 50, 100, 200, 500, 1000];
        const sugerencias = [];
        
        billetes.forEach(b => {
            if (b > total && sugerencias.length < 3) {
                sugerencias.push(b);
            }
        });

        // Alternativa múltiple exacta de 10 o 50 extra
        const exactoTeorico = Math.ceil(total / 50) * 50;
        if (!sugerencias.includes(exactoTeorico) && exactoTeorico > total) {
            sugerencias.push(exactoTeorico);
        }

        sugerencias.sort((a,b) => a-b);

        sugerenciasList.innerHTML = `
            <button class="btn-sugerencia-cash" onclick="CajaController.rellenarMontoEfectivo(${total})">Exacto: ${formatCurrency(total)}</button>
            ${sugerencias.map(s => `
                <button class="btn-sugerencia-cash" onclick="CajaController.rellenarMontoEfectivo(${s})">${formatCurrency(s)}</button>
            `).join('')}
        `;

        // Limpiar input y cambio
        const inputPago = document.getElementById('caja-recibido-input');
        if (inputPago) {
            inputPago.value = '';
            inputPago.setAttribute('min', total);
        }
        const spanCambio = document.getElementById('caja-cambio-calculado');
        if (spanCambio) spanCambio.textContent = formatCurrency(0);
    },

    rellenarMontoEfectivo(monto) {
        const input = document.getElementById('caja-recibido-input');
        if (!input) return;
        input.value = monto.toFixed(2);
        this.recalcularCambio();
    },

    recalcularCambio() {
        if (!this.pedidoSeleccionado) return;
        const total = this.pedidoSeleccionado.total;
        const inputVal = parseFloat(document.getElementById('caja-recibido-input').value) || 0;
        const spanCambio = document.getElementById('caja-cambio-calculado');
        
        if (inputVal >= total) {
            const cambio = inputVal - total;
            spanCambio.textContent = formatCurrency(cambio);
            spanCambio.style.color = '#10B981';
        } else {
            spanCambio.textContent = 'Monto insuficiente';
            spanCambio.style.color = '#EF4444';
        }
    },

    async ejecutarCobro(metodoPago) {
        if (!this.pedidoSeleccionado) {
            alert("No hay ningún pedido abierto.");
            return;
        }

        const total = this.pedidoSeleccionado.total;

        if (metodoPago === 'efectivo') {
            const cobrado = parseFloat(document.getElementById('caja-recibido-input').value) || 0;
            if (cobrado < total) {
                alert("El efectivo entregado es menor que el total de la cuenta.");
                return;
            }
        }

        const confirmacion = confirm(`¿Confirmar cobro de ${formatCurrency(total)} por método "${metodoPago.toUpperCase()}"?`);
        if (!confirmacion) return;

        try {
            await DataService.cobrarsePedido(
                this.pedidoSeleccionado.id, 
                total, 
                metodoPago, 
                this.pedidoSeleccionado.mesero
            );

            alert("Pago procesado con éxito. Mesa liberada.");
            this.cerrarDetalle();
            await this.cargarYRenderizar();
        } catch (e) {
            alert("Ocurrió un error al procesar el cobro: " + e.message);
        }
    }
};

// ----------------------------------------------------
// 4. CONTROLADOR DE AUDITORÍA (Dashboards & Reporting)
// ----------------------------------------------------
const AuditoriaController = {
    logs: [],

    async init() {
        console.log("📊 Auditoría: Cargando reportes financieros...");
        await this.cargarYVisualizar();

        // Enlace en tiempo real de transacciones para actualizar KPIs en caliente
        window.onRealtimeAuditoriaUpdate = async (payload) => {
            console.log("La auditoría captó una transacción de caja. Actualizando...");
            await this.cargarYVisualizar();
        };
    },

    async cargarYVisualizar() {
        try {
            this.logs = await DataService.fetchAuditoria();
            this.calcularKPIs();
            this.renderLogsTable();
        } catch (e) {
            console.error("Error al cargar auditoría financiera:", e);
        }
    },

    calcularKPIs(logsParaCalcular) {
        const spanVentas = document.getElementById('kpi-ventas-totales');
        const spanPedidos = document.getElementById('kpi-transacciones-recuento');
        const spanTicket = document.getElementById('kpi-ticket-promedio');
        const spanPopular = document.getElementById('kpi-metodo-popular');

        if (!spanVentas) return;

        const listado = logsParaCalcular || this.logs;

        const total = listado.reduce((acc, current) => acc + parseFloat(current.monto), 0);
        const count = listado.length;
        const avg = count > 0 ? (total / count) : 0;

        // Distribución de métodos de pago
        const metodos = { efectivo: 0, tarjeta: 0, transferencia: 0 };
        listado.forEach(l => {
            const mp = l.metodo_pago ? l.metodo_pago.toLowerCase() : 'desconocido';
            if (metodos.hasOwnProperty(mp)) {
                metodos[mp]++;
            } else {
                metodos[mp] = 1;
            }
        });

        let popular = 'N/A';
        let maxCount = -1;
        Object.entries(metodos).forEach(([k, v]) => {
            if (v > maxCount && v > 0) {
                maxCount = v;
                popular = k.toUpperCase() + ` (${v} u.)`;
            }
        });

        // Escribir en DOM
        spanVentas.textContent = formatCurrency(total);
        spanPedidos.textContent = count;
        spanTicket.textContent = formatCurrency(avg);
        spanPopular.textContent = popular;
    },

    renderLogsTable(logsParaRenderizar) {
        const tbody = document.getElementById('tbody-auditoria-logs');
        if (!tbody) return;

        tbody.innerHTML = '';
        const listado = logsParaRenderizar || this.logs;

        if (listado.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="6" class="text-center" style="padding: 30px; color: var(--color-text-muted);">
                        No se encontraron transacciones con los criterios seleccionados.
                    </td>
                </tr>
            `;
            return;
        }

        listado.forEach(log => {
            const fDate = new Date(log.creado_en);
            const timeStr = fDate.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            const dateStr = fDate.toLocaleDateString('es-MX');

            const tr = document.createElement('tr');
            
            // Estilos estéticos de insignia de método de pago
            let badgeClass = 'badge-tarjeta';
            if (log.metodo_pago === 'efectivo') badgeClass = 'badge-efectivo';
            if (log.metodo_pago === 'transferencia') badgeClass = 'badge-transferencia';

            // Insignia estilizada de Mesa si viene vinculada
            const mesaBadge = log.pedidos && log.pedidos.mesa 
                ? `<span class="aud-pedido-id" style="background-color: rgba(139, 92, 246, 0.15); color: #8B5CF6; border-color: #8B5CF6; margin-left: 8px;">${log.pedidos.mesa}</span>`
                : '';

            tr.innerHTML = `
                <td><strong>#${log.id}</strong></td>
                <td><span class="aud-pedido-id">Pedido: #${log.pedido_id}</span>${mesaBadge}</td>
                <td><span class="estado-pago-pill ${badgeClass}">${log.metodo_pago.toUpperCase()}</span></td>
                <td>${log.mesero}</td>
                <td><span class="precio-col-text">${formatCurrency(log.monto)}</span></td>
                <td style="color: var(--color-text-muted); font-size: 0.9rem;">
                    ${dateStr} <span style="font-weight:600; color:var(--color-primary);">${timeStr}</span>
                </td>
            `;
            tbody.appendChild(tr);
        });
    },

    // Filtrado dinámico multi-criterio avanzado
    filtrarYBuscar() {
        const queryGlobal = (document.getElementById('filtro-auditoria-input')?.value || '').trim().toLowerCase();
        const queryMesa = (document.getElementById('filtro-auditoria-mesa')?.value || '').trim().toLowerCase();
        const queryFecha = (document.getElementById('filtro-auditoria-fecha')?.value || '').trim(); // Formato YYYY-MM-DD

        const filtrados = this.logs.filter(log => {
            // 1. Filtrado de Texto General (Mesero, Método de Pago o ID)
            let matchGlobal = true;
            if (queryGlobal) {
                const mesero = (log.mesero || '').toLowerCase();
                const metodo = (log.metodo_pago || '').toLowerCase();
                const pedId = String(log.pedido_id);
                matchGlobal = mesero.includes(queryGlobal) || metodo.includes(queryGlobal) || pedId.includes(queryGlobal);
            }

            // 2. Filtrado de Mesa
            let matchMesa = true;
            if (queryMesa) {
                const mesa = (log.pedidos && log.pedidos.mesa ? log.pedidos.mesa : '').toLowerCase();
                matchMesa = mesa.includes(queryMesa);
            }

            // 3. Filtrado de Fecha (Calendar/Date input)
            let matchFecha = true;
            if (queryFecha) {
                const logFechaStr = log.creado_en.split('T')[0]; // "YYYY-MM-DD"
                matchFecha = (logFechaStr === queryFecha);
            }

            return matchGlobal && matchMesa && matchFecha;
        });

        this.logsFiltrados = filtrados;
        this.renderLogsTable(filtrados);
        this.calcularKPIs(filtrados);
    },

    limpiarFiltros() {
        const inpGlobal = document.getElementById('filtro-auditoria-input');
        const inpMesa = document.getElementById('filtro-auditoria-mesa');
        const inpFecha = document.getElementById('filtro-auditoria-fecha');

        if (inpGlobal) inpGlobal.value = '';
        if (inpMesa) inpMesa.value = '';
        if (inpFecha) inpFecha.value = '';

        this.logsFiltrados = null;
        this.renderLogsTable();
        this.calcularKPIs();
        mostrarNotificacionFlotante("Filtros restablecidos");
    },

    exportarCSV() {
        const listado = this.logsFiltrados || this.logs;
        if (listado.length === 0) {
            alert("No hay registros financieros que coincidan con la vista para exportar.");
            return;
        }

        // Columnas CSV
        const encabezados = ["ID Transaccion", "ID Pedido", "Mesa", "Metodo de Pago", "Mesero Atendiendo", "Monto de Cobro (MXN)", "Fecha y Hora UTC"];
        const filas = listado.map(log => [
            log.id,
            log.pedido_id,
            `"${(log.pedidos && log.pedidos.mesa ? log.pedidos.mesa : 'N/A').replace(/"/g, '""')}"`,
            log.metodo_pago.toUpperCase(),
            `"${log.mesero.replace(/"/g, '""')}"`,
            log.monto,
            log.creado_en
        ]);

        const contenido = [encabezados.join(","), ...filas.map(f => f.join(","))].join("\n");
        
        // Crear enlace de descarga inmutable
        const blob = new Blob([new Uint8Array([0xEF, 0xBB, 0xBF]), contenido], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement("a");
        const url = URL.createObjectURL(blob);
        
        const f = new Date();
        const stamp = `${f.getFullYear()}-${f.getMonth()+1}-${f.getDate()}`;

        link.setAttribute("href", url);
        link.setAttribute("download", `auditoria_financiera_restaurante_${stamp}.csv`);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        
        mostrarNotificacionFlotante("CSV exportado y descargado exitosamente");
    }
};

// ----------------------------------------------------
// 5. CONFIGURACIÓN MODAL DE CONEXIÓN CON SUPABASE
// ----------------------------------------------------
const SettingsModal = {
    abrir() {
        let el = document.getElementById('settings-supabase-modal');
        if (!el) {
            el = this.crearModalDom();
        }
        el.style.display = 'flex';
        
        // Cargar config actual en inputs
        const config = DataService.getConfig();
        document.getElementById('input-set-url').value = config.url || '';
        document.getElementById('input-set-key').value = config.anonKey || '';
    },

    cerrar() {
        const el = document.getElementById('settings-supabase-modal');
        if (el) el.style.display = 'none';
    },

    guardar() {
        const url = document.getElementById('input-set-url').value.trim();
        const key = document.getElementById('input-set-key').value.trim();

        if (url && !url.startsWith("http")) {
            alert("La URL de Supabase debe comenzar con http:// o https://");
            return;
        }

        DataService.saveConfig(url, key);
        alert("Configuración guardada. La página se recargará para conectar con Supabase.");
        this.cerrar();
    },

    desvincular() {
        if (confirm("¿Estás seguro de que deseas eliminar la configuración de Supabase? El sistema dejará de funcionar hasta que se configure de nuevo.")) {
            DataService.resetConfig();
            this.cerrar();
        }
    },

    crearModalDom() {
        const modal = document.createElement('div');
        modal.id = 'settings-supabase-modal';
        modal.className = 'supabase-config-modal-overlay';
        
        modal.innerHTML = `
            <div class="supabase-config-modal-card">
                <div class="modal-card-header">
                    <h3>⚙️ Conexión Supabase (PostgreSQL Realtime)</h3>
                    <button class="btn-close-modal" onclick="SettingsModal.cerrar()">×</button>
                </div>
                <div class="modal-card-body">
                    <p class="modal-instruccion-text">
                        Ingresa las credenciales de tu proyecto Supabase para sincronizar la caja, cocina y App de meseros de forma real en la nube:
                    </p>
                    <div class="form-group-modal">
                        <label for="input-set-url">SUPABASE URL</label>
                        <input type="text" id="input-set-url" placeholder="https://abcde12345.supabase.co" />
                    </div>
                    <div class="form-group-modal">
                        <label for="input-set-key">SUPABASE ANON KEY</label>
                        <textarea id="input-set-key" rows="2" placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."></textarea>
                    </div>
                    <div class="status-connection-indicator">
                        Estatus actual: <strong>${DataService.isReal() ? '🟢 Conectado a la Nube' : '🔴 Supabase No Configurado'}</strong>
                    </div>
                </div>
                <div class="modal-card-footer">
                    ${DataService.isReal() ? `<button class="btn-modal-action btn-danger-modal" onclick="SettingsModal.desvincular()">Eliminar Configuración</button>` : ''}
                    <button class="btn-modal-action btn-secondary-modal" onclick="SettingsModal.cerrar()">Cancelar</button>
                    <button class="btn-modal-action btn-success-modal" onclick="SettingsModal.guardar()">Guardar y Conectar</button>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
        return modal;
    }
};

// Adjuntar globalmente para invocación desde el HTML
window.SettingsModal = SettingsModal;
window.CocinaController = CocinaController;
window.CajaController = CajaController;
window.AuditoriaController = AuditoriaController;

// ----------------------------------------------------
// 5. CONTROLADOR DE SESIÓN DE OPERADOR / MESERO (LOCALSTORAGE)
// ----------------------------------------------------
const WaiterSession = {
    getKey() {
        return 'mesero_nombre';
    },

    getNombre() {
        return localStorage.getItem(this.getKey()) || '';
    },

    setNombre(nombre) {
        localStorage.setItem(this.getKey(), nombre.trim());
        this.actualizarUI();
    },

    init() {
        console.log("🤵 Sesión: Verificando identidad de operador...");
        const actual = this.getNombre();

        // Inyectar visualización de nombre en el navbar/header de las vistas
        this.actualizarUI();

        if (!actual) {
            this.abrirPrompt();
        }
    },

    actualizarUI() {
        const nombre = this.getNombre();
        
        // Agregar un indicador visual elegante en el panel derecho del navbar
        const paneles = document.querySelectorAll('.right-control-panel');
        paneles.forEach(panel => {
            let badge = panel.querySelector('#operador-visual-badge');
            if (!badge) {
                badge = document.createElement('div');
                badge.id = 'operador-visual-badge';
                badge.style.display = 'flex';
                badge.style.alignItems = 'center';
                badge.style.gap = '6px';
                badge.style.fontSize = '0.85rem';
                badge.style.backgroundColor = 'rgba(241, 245, 249, 0.08)';
                badge.style.padding = '4px 10px';
                badge.style.borderRadius = '20px';
                badge.style.border = '1px solid var(--color-border)';
                badge.style.marginRight = '8px';
                badge.style.color = 'var(--color-primary, #E2E8F0)';
                badge.style.cursor = 'pointer';
                badge.onclick = () => this.abrirPromptEditable();
                panel.insertBefore(badge, panel.firstChild);
            }
            badge.innerHTML = `👤 <span style="font-weight: 700;">${nombre || 'Configurar Nombre'}</span>`;
        });
    },

    abrirPrompt() {
        let el = document.getElementById('welcome-operator-modal');
        if (!el) {
            el = this.crearWelcomeModalDom(false); // No descartable la primera vez
        }
        el.style.display = 'flex';
    },

    abrirPromptEditable() {
        let el = document.getElementById('welcome-operator-modal');
        if (el) el.remove();
        el = this.crearWelcomeModalDom(true); // Descartable desde el perfil
        el.style.display = 'flex';
        document.getElementById('welcome-operator-input').value = this.getNombre();
    },

    cerrar() {
        const el = document.getElementById('welcome-operator-modal');
        if (el) el.style.display = 'none';
    },

    guardar() {
        const input = document.getElementById('welcome-operator-input');
        if (!input) return;
        const nombreVal = input.value.trim();
        if (!nombreVal) {
            alert("Por favor, ingresa tu nombre de operador para continuar.");
            return;
        }
        this.setNombre(nombreVal);
        this.cerrar();
    },

    crearWelcomeModalDom(descartable) {
        const modal = document.createElement('div');
        modal.id = 'welcome-operator-modal';
        modal.className = 'supabase-config-modal-overlay';
        
        // Si no es descartable, evitamos clicks de escape accidental
        if (!descartable) {
            modal.style.pointerEvents = 'auto';
        }

        modal.innerHTML = `
            <div class="supabase-config-modal-card">
                <div class="modal-card-header" style="justify-content: center; text-align: center; flex-direction: column; gap: 8px; padding-top: 1.5rem; border-bottom: none;">
                    <div style="font-size: 3rem; margin-bottom: 0.5rem;">👋</div>
                    <h3 style="font-size: 1.25rem;">¡Bienvenido a RestFlow Web!</h3>
                </div>
                <div class="modal-card-body" style="text-align: center; padding: 1.5rem;">
                    <p style="font-size: 0.85rem; color: var(--color-text-muted); line-height: 1.4; margin-bottom: 1.5rem;">
                        Por favor, escribe tu nombre o rol de operador para personalizar y registrar de forma correcta tus actividades de mesa, cocina y caja.
                    </p>
                    <div class="form-group-modal" style="text-align: left;">
                        <label for="welcome-operator-input" style="letter-spacing: 0.05em; font-size: 0.75rem; font-weight: 700; color: var(--color-primary); display: block; margin-bottom: 0.5rem;">TU NOMBRE O ROL</label>
                        <input type="text" id="welcome-operator-input" placeholder="Ej: Cajero Carlos, Chef Julia" style="padding: 10px; border-radius: 6px; background-color: var(--color-bg-surface); color: var(--color-text-main); border: 2px solid var(--color-border); font-size: 0.95rem; font-family: inherit; width: 100%; box-sizing: border-box;" />
                    </div>
                </div>
                <div class="modal-card-footer" style="${!descartable ? 'justify-content: center;' : ''}">
                    ${descartable ? '<button class="btn-modal-action btn-secondary-modal" onclick="WaiterSession.cerrar()">Cancelar</button>' : ''}
                    <button class="btn-modal-action btn-success-modal" onclick="WaiterSession.guardar()" style="${!descartable ? 'width: 100%;' : ''}">Comenzar Ahora</button>
                </div>
            </div>
        `;

        // Soporte de tecla Enter en el input
        const input = modal.querySelector('#welcome-operator-input');
        if (input) {
            input.onkeydown = (e) => {
                if (e.key === 'Enter') {
                    this.guardar();
                }
            };
        }

        document.body.appendChild(modal);
        return modal;
    }
};

window.WaiterSession = WaiterSession;

// ----------------------------------------------------
// 6. CONTROLADOR DE EDICIÓN DEL MENÚ (LOCALSTORAGE)
// ----------------------------------------------------
const MenuEditor = {
    abrirModal() {
        let el = document.getElementById('menu-editor-modal');
        if (el) el.remove();
        el = this.crearModalDom();
        el.style.display = 'flex';
        this.renderList();
    },

    cerrar() {
        const el = document.getElementById('menu-editor-modal');
        if (el) el.style.display = 'none';
    },

    crearModalDom() {
        const modal = document.createElement('div');
        modal.id = 'menu-editor-modal';
        modal.className = 'supabase-config-modal-overlay';
        modal.style.display = 'none';

        modal.innerHTML = `
            <div class="supabase-config-modal-card" style="max-width: 500px; width: 90%;">
                <div class="modal-card-header" style="padding-top: 1.25rem;">
                    <h3 style="font-size: 1.15rem; display: flex; align-items: center; gap: 8px;">📋 Configuración del Menú</h3>
                    <button class="close-modal-btn" onclick="MenuEditor.cerrar()" style="background:none; border:none; cursor:pointer; font-size:1.25rem; color:var(--color-text-muted);">✕</button>
                </div>
                <div class="modal-card-body" style="padding: 1.25rem;">
                    <p style="font-size: 0.8rem; color: var(--color-text-muted); margin-bottom: 1rem; line-height: 1.4;">
                        Agrega nuevos platillos, modifica precios de los asignados, o vacía el menú por completo para redactarlo de cero según corresponda.
                    </p>
                    
                    <!-- Botones de Acción Global -->
                    <div style="display: flex; gap: 10px; margin-bottom: 1rem; justify-content: flex-end;">
                        <button class="btn-modal-action" onclick="MenuEditor.vaciarMenu()" style="background-color: var(--color-danger, #EF4444); color: white; font-size: 0.75rem; padding: 4px 10px; border-radius: 4px; font-weight: bold; cursor: pointer; border: none;">🗑️ Quitar Todo</button>
                        <button class="btn-modal-action" onclick="MenuEditor.restablecerMenu()" style="background-color: #4F378B; color: white; font-size: 0.75rem; padding: 4px 10px; border-radius: 4px; font-weight: bold; cursor: pointer; border: none;">🔄 Restablecer</button>
                    </div>

                    <!-- Lista de Platillos Activos -->
                    <div id="editor-active-items" style="display: flex; flex-direction: column; gap: 8px; margin-bottom: 1.5rem; max-height: 180px; overflow-y: auto; background-color: var(--color-bg-depth); padding: 8px; border-radius: 6px; border: 1px solid var(--color-border);">
                        <!-- Render interactivo de platillos aquí -->
                    </div>

                    <!-- Formulario de Nuevo Platillo -->
                    <fieldset style="border: 1px solid var(--color-border); border-radius: 6px; padding: 12px; background-color: rgba(255,255,255,0.02)">
                        <legend style="font-size: 0.75rem; font-weight: bold; color: var(--color-primary); padding: 0 6px;">🆕 AGREGAR PLATILLO</legend>
                        <div style="display: flex; gap: 8px; align-items: flex-end; width:100%; box-sizing:border-box;">
                            <div style="flex: 2;">
                                <label style="font-size: 0.7rem; font-weight: bold; display: block; margin-bottom: 4px;">Nombre del Plato</label>
                                <input id="new-item-name" type="text" placeholder="Ej: Pizza Hawayana" style="width: 100%; padding: 8px; border-radius: 4px; background-color: var(--color-bg-surface); color: var(--color-text-main); border: 1px solid var(--color-border); font-size: 0.85rem;" />
                            </div>
                            <div style="flex: 1;">
                                <label style="font-size: 0.7rem; font-weight: bold; display: block; margin-bottom: 4px;">Precio ($)</label>
                                <input id="new-item-price" type="number" step="0.01" placeholder="Ej: 9.50" style="width: 100%; padding: 8px; border-radius: 4px; background-color: var(--color-bg-surface); color: var(--color-text-main); border: 1px solid var(--color-border); font-size: 0.85rem;" />
                            </div>
                            <button onclick="MenuEditor.agregarItem()" style="background-color: var(--color-primary); color: #1E293B; border: none; border-radius: 4px; padding: 8px 14px; font-size: 0.8rem; font-weight: bold; height: 33px; cursor: pointer;">Añadir</button>
                        </div>
                    </fieldset>
                </div>
                <div class="modal-card-footer" style="padding: 1rem 1.25rem;">
                    <button class="btn-modal-action btn-secondary-modal" onclick="MenuEditor.cerrar()">Cancelar</button>
                    <button class="btn-modal-action btn-success-modal" onclick="MenuEditor.guardar()">Guardar Cambios</button>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
        return modal;
    },

    itemsTemp: {},

    renderList() {
        const container = document.getElementById('editor-active-items');
        if (!container) return;

        // Cargar menú en estado temporal al abrir si está vacío
        if (!this.itemsTemp || Object.keys(this.itemsTemp).length === 0) {
            this.itemsTemp = { ...MENU_RESTAURANTE };
        }

        const entries = Object.entries(this.itemsTemp);
        if (entries.length === 0) {
            container.innerHTML = `<div style="text-align: center; color: var(--color-text-muted); font-size: 0.8rem; padding: 12px;">🚫 El menú está vacío. Agrega platos para comenzar.</div>`;
            return;
        }

        container.innerHTML = entries.map(([nombre, precio], idx) => `
            <div style="display: flex; gap: 8px; justify-content: space-between; align-items: center; border-bottom: 1px dashed var(--color-border); padding-bottom: 6px;">
                <span style="font-size: 0.8rem; font-weight: bold; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 2; color: var(--color-text-main);">${nombre}</span>
                <input id="price-edit-${idx}" type="number" step="0.01" value="${precio}" onchange="MenuEditor.editarPrecio('${nombre}', this.value)" style="width: 75px; padding: 2px 6px; border-radius: 4px; background-color: var(--color-bg-surface); color: var(--color-text-main); border: 1px solid var(--color-border); font-size: 0.8rem; text-align: right;" />
                <button onclick="MenuEditor.eliminarItem('${nombre}')" style="background: none; border: none; cursor: pointer; font-size: 1rem;" title="Eliminar plato">🗑️</button>
            </div>
        `).join('');
    },

    editarPrecio(nombre, valor) {
        const p = parseFloat(valor);
        if (!isNaN(p) && p >= 0) {
            this.itemsTemp[nombre] = p;
        }
    },

    eliminarItem(nombre) {
        delete this.itemsTemp[nombre];
        this.renderList();
    },

    vaciarMenu() {
        if (confirm("¿Estás seguro de que deseas vaciar todo el menú? Deberás configurar nuevos platillos.")) {
            this.itemsTemp = {};
            this.renderList();
        }
    },

    restablecerMenu() {
        this.itemsTemp = { ...MENU_RESTAURANTE_DEFAULT };
        this.renderList();
    },

    agregarItem() {
        const nameInput = document.getElementById('new-item-name');
        const priceInput = document.getElementById('new-item-price');
        if (!nameInput || !priceInput) return;

        const name = nameInput.value.trim();
        const priceObj = parseFloat(priceInput.value);

        if (!name) {
            alert("El nombre del platillo es requerido.");
            return;
        }
        if (isNaN(priceObj) || priceObj < 0) {
            alert("Ingresa un precio numérico válido.");
            return;
        }

        this.itemsTemp[name] = priceObj;
        nameInput.value = '';
        priceInput.value = '';
        this.renderList();
    },

    guardar() {
        MENU_RESTAURANTE = { ...this.itemsTemp };
        localStorage.setItem('custom_menu_restaurante', JSON.stringify(MENU_RESTAURANTE));
        this.cerrar();
        alert("¡Menú guardado y actualizado con éxito!");
        if (window.CocinaController && typeof window.CocinaController.renderMenu === 'function') {
            window.CocinaController.renderMenu();
        }
        this.itemsTemp = {};
    }
};

window.MenuEditor = MenuEditor;

// ----------------------------------------------------
// 7. MONITOR DE CONEXIÓN CON SUPABASE
// ----------------------------------------------------
const ConnectionMonitor = {
    async init() {
        const paneles = document.querySelectorAll('.right-control-panel');
        paneles.forEach(panel => {
            let statusBadge = panel.querySelector('#supabase-connection-status');
            if (!statusBadge) {
                statusBadge = document.createElement('div');
                statusBadge.id = 'supabase-connection-status';
                statusBadge.style.display = 'flex';
                statusBadge.style.alignItems = 'center';
                statusBadge.style.gap = '6px';
                statusBadge.style.fontSize = '0.75rem';
                statusBadge.style.padding = '4px 10px';
                statusBadge.style.borderRadius = '20px';
                statusBadge.style.border = '1px solid var(--color-border)';
                statusBadge.style.marginLeft = '8px';
                statusBadge.style.cursor = 'help';
                panel.appendChild(statusBadge);
            }
            this.updateUI(statusBadge, { success: false, message: "Verificando..." });
        });

        await this.check();
        // Verificar periódicamente
        setInterval(() => this.check(), 30000); 
    },

    async check() {
        if (typeof DataService !== 'undefined' && DataService.checkConnection) {
            const result = await DataService.checkConnection();
            const badges = document.querySelectorAll('#supabase-connection-status');
            badges.forEach(badge => this.updateUI(badge, result));
        }
    },

    updateUI(el, result) {
        const isReal = DataService.isReal();
        const color = result.success ? '#10B981' : (isReal ? '#EF4444' : '#F59E0B');
        const icon = result.success ? '●' : '○';
        el.style.borderColor = color;
        el.style.color = color;
        el.innerHTML = `<span style="font-size: 1.2rem; line-height: 1;">${icon}</span> <span style="font-weight: 600;">${result.message}</span>`;
        el.title = result.message;
    }
};

document.addEventListener('DOMContentLoaded', () => {
    WaiterSession.init();
    ConnectionMonitor.init();
});
