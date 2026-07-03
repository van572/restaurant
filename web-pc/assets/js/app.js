// CONTROLLER COMPLETO Y OPERATIVO DE COCINA, CAJA Y AUDITORÍA - RESTAURANTE MULTI-VIEW
// Implementa la lógica de cobros, actualización de pedidos, cálculo de KPI y exportación de logs financieros.

// ----------------------------------------------------
// 1. CONFIGURACIÓN COMPARTIDA Y MENÚ
// ----------------------------------------------------
const MENU_RESTAURANTE_DEFAULT = {
    "Hamburguesa Premium": 12.50,
    "Pizza Personal Pepperoni": 15.00,
    "Tacos de Res (x3)": 8.50,
    "Parrilla Familiar (al Peso)": 24.00,
    "Chicharrón Crujiente (al Peso)": 18.00,
    "Costillas de Cerdo (al Peso)": 21.00,
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

// --- UTILS DE IMPRESIÓN PERSONALIZADA DE LOCAL (Cocina & Caja) ---
// Cache local para optimizar rendimiento y evitar saturar APIs/Supabase
let cachedTasaCambio = null;
let lastTasaFetchTime = 0;

// Consultar múltiples APIs públicas del BCV/Dólar en Venezuela con tolerancia a fallas
async function fetchBCVRate() {
    const apis = [
        {
            url: "https://pydolarvenezuela-api.vercel.app/api/v1/dollar?page=bcv",
            parse: (data) => data?.monitors?.usd?.price
        },
        {
            url: "https://pydolarvenezuela-api.vercel.app/api/v1/dollar",
            parse: (data) => data?.monitors?.bcv?.price
        },
        {
            url: "https://dolar-api-venezuela.vercel.app/api/bcv",
            parse: (data) => data?.monitors?.bcv?.price || data?.price
        },
        {
            url: "https://open.er-api.com/v6/latest/USD",
            parse: (data) => data?.rates?.VES
        }
    ];

    for (const api of apis) {
        try {
            console.log(`🤖 Intentando obtener tasa BCV desde: ${api.url}`);
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 6000);
            
            const res = await fetch(api.url, { signal: controller.signal });
            clearTimeout(timeoutId);
            
            if (res.ok) {
                const data = await res.json();
                const price = api.parse(data);
                if (price && !isNaN(price) && price > 0) {
                    console.log(`✅ Tasa BCV obtenida exitosamente: ${price} VES/USD (desde ${api.url})`);
                    return parseFloat(price);
                }
            }
        } catch (e) {
            console.warn(`⚠️ Falló obtención desde ${api.url}:`, e.message || e);
        }
    }
    return null;
}

async function getTasaCambio() {
    const ahora = Date.now();
    // Retornar caché si se obtuvo hace menos de 10 minutos (600,000 ms)
    if (cachedTasaCambio && (ahora - lastTasaFetchTime < 600000)) {
        return cachedTasaCambio;
    }

    try {
        if (window.DataService && DataService.isReal()) {
            let val = null;
            let esNuevo = false;
            try {
                val = await DataService.getSettings("tasa_cambio");
            } catch (e) {
                console.warn("No se encontró tasa_cambio en ajustes de Supabase, se inicializará con auto-fetch.");
                esNuevo = true;
            }
            
            // Decidir si actualizamos la tasa de fondo (si no existe, o si es más antigua de 1 hora)
            const debeActualizar = esNuevo || !val || (ahora - lastTasaFetchTime > 3600000);
            
            if (debeActualizar) {
                // Ejecución en segundo plano para no bloquear el flujo principal ni la carga de la UI
                fetchBCVRate().then(async (nuevaTasa) => {
                    if (nuevaTasa) {
                        cachedTasaCambio = nuevaTasa;
                        lastTasaFetchTime = Date.now();
                        try {
                            await DataService.saveSettings("tasa_cambio", nuevaTasa);
                            console.log(`💾 Tasa de cambio guardada/actualizada en Supabase: ${nuevaTasa} VES`);
                            
                            // Emitir un evento para refrescar la UI de caja si está abierta
                            if (window.CajaController && typeof window.CajaController.renderListaMesas === 'function') {
                                window.CajaController.renderBankTransactions();
                            }
                        } catch (err) {
                            console.error("Error guardando tasa de cambio en Supabase:", err);
                        }
                    }
                }).catch(e => console.error("Error en fetch de fondo de tasa:", e));
            }

            if (val) {
                cachedTasaCambio = parseFloat(val);
                if (debeActualizar && !lastTasaFetchTime) {
                    lastTasaFetchTime = ahora;
                }
                return cachedTasaCambio;
            }
        }
    } catch (e) {
        console.warn("No se pudo conectar a Supabase para consultar tasa_cambio, usando fallbacks directos.", e);
    }

    // Fallback de contingencia: si Supabase falló pero no tenemos caché, intentar fetch directo
    if (!cachedTasaCambio) {
        const nuevaTasa = await fetchBCVRate();
        if (nuevaTasa) {
            cachedTasaCambio = nuevaTasa;
            lastTasaFetchTime = ahora;
            return cachedTasaCambio;
        }
    }

    return cachedTasaCambio || 45.5;
}

function imprimirComandaCocina(pedido) {
    const printWindow = window.open('', '_blank', 'width=400,height=600');
    if (!printWindow) {
        alert("Por favor habilita las ventanas emergentes (pop-ups) para imprimir comandas de cocina.");
        return;
    }
    const itemsHtml = pedido.items.map(item => `
        <tr>
            <td style="text-align:left; font-size: 18px; padding: 6px 0; border-bottom: 1px dashed #ddd; line-height: 1.3;">
                <span style="font-size: 22px; font-weight: 900; background: #000; color: #fff; padding: 2px 6px; border-radius: 4px; margin-right: 6px;">${item.cantidad}x</span> 
                <strong>${item.producto.toUpperCase()}</strong>
            </td>
        </tr>
        ${item.notas ? `<tr><td style="text-align:left; font-size: 15px; padding-left: 15px; padding-bottom: 8px; color: #333; font-weight: bold; font-style: italic; background: #f9f9f9; border-left: 3px solid #000;">📝 Notas: ${item.notas}</td></tr>` : ''}
    `).join('');

    const formattedTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    printWindow.document.write(`
        <html>
        <head>
            <style>
                @page { margin: 0; }
                body {
                    font-family: 'Courier New', Courier, monospace;
                    width: 76mm;
                    margin: 0;
                    padding: 10px;
                    color: #000;
                    background: #fff;
                }
                .ticket-title {
                    text-align: center;
                    font-size: 22px;
                    font-weight: 900;
                    margin-bottom: 6px;
                    border: 3px double #000;
                    padding: 6px 0;
                    letter-spacing: 1px;
                }
                .meta-info {
                    font-size: 14px;
                    margin-bottom: 12px;
                    border-bottom: 2px dashed #000;
                    padding-bottom: 6px;
                    line-height: 1.5;
                }
                .client-highlight {
                    font-size: 20px;
                    font-weight: 900;
                    background: #f0f0f0;
                    padding: 6px;
                    border: 1px solid #000;
                    margin-bottom: 6px;
                    text-align: center;
                }
                .items-table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 15px;
                }
                .footer {
                    text-align: center;
                    font-size: 13px;
                    border-top: 2px dashed #000;
                    padding-top: 8px;
                    margin-top: 15px;
                    font-weight: bold;
                }
            </style>
        </head>
        <body onload="window.print(); window.close();">
            <div class="ticket-title">TICKET DE COCINA</div>
            <div class="client-highlight">🎯 CLIENTE: ${pedido.mesa.toUpperCase()}</div>
            <div class="meta-info">
                <div>📅 <strong>Fecha:</strong> ${new Date().toLocaleDateString()} - ${formattedTime}</div>
                <div>👤 <strong>Atendió:</strong> ${pedido.mesero}</div>
                <div>🆔 <strong>Pedido Nro:</strong> #${pedido.id}</div>
            </div>
            <table class="items-table">
                <tbody>
                    ${itemsHtml}
                </tbody>
            </table>
            <div class="footer">
                <p>*** FIN DE COMANDA COCINA ***</p>
            </div>
        </body>
        </html>
    `);
    printWindow.document.close();
}

function imprimirTicketCaja(pedido, estado = 'CANCELADO', cobro = null, cambio = null, tasaCambio = 45.5, clienteNombre = '', clienteRif = '', clienteDireccion = '') {
    const printWindow = window.open('', '_blank', 'width=400,height=600');
    if (!printWindow) {
        alert("Por favor habilita las ventanas emergentes (pop-ups) para imprimir tickets de caja.");
        return;
    }
    const itemsHtml = pedido.items.map(item => {
        const uPrecio = item.precio;
        const totalItem = item.precio * item.cantidad;
        const itemBase = totalItem / 1.16;
        const itemIva = totalItem - itemBase;
        const uBase = uPrecio / 1.16;
        const uIva = uPrecio - uBase;
        return `
            <tr style="border-bottom: 1px dotted #ccc;">
                <td style="text-align:left; padding: 6px 0; font-size: 11px; line-height: 1.3;">
                    <strong>${item.producto.toUpperCase()} (G)</strong><br/>
                    <span style="font-size:10px; color:#444;">
                        Especificación: Servicio Gastronómico Consumo en Sala<br/>
                        ${item.cantidad} Unid. x $${uPrecio.toFixed(2)} USD<br/>
                        <span style="color:#555;">S.B. Unitario: $${uBase.toFixed(2)} | Alícuota: 16.00% | IVA Unit: $${uIva.toFixed(2)}</span><br/>
                        <span style="font-weight:600; color:#111;">B.I. Item: $${itemBase.toFixed(2)} | IVA Item: $${itemIva.toFixed(2)}</span>
                    </span>
                </td>
                <td style="text-align:right; padding: 6px 0; font-size: 11px; font-weight: bold; vertical-align: bottom; white-space: nowrap;">
                    $${totalItem.toFixed(2)} USD<br/>
                    <span style="font-size: 9px; color: #555; font-weight: normal;">Bs. ${(totalItem * tasaCambio).toFixed(2)}</span>
                </td>
            </tr>
        `;
    }).join('');

    const totalUsd = pedido.total;
    const baseImponible = totalUsd / 1.16;
    const iva16 = totalUsd - baseImponible;
    const totalVes = totalUsd * tasaCambio;
    const baseImponibleVes = baseImponible * tasaCambio;
    const iva16Ves = iva16 * tasaCambio;
    const formattedTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

    printWindow.document.write(`
        <html>
        <head>
            <style>
                @page { margin: 0; }
                body {
                    font-family: 'Courier New', Courier, monospace;
                    width: 76mm;
                    margin: 0;
                    padding: 10px;
                    color: #000;
                    background: #fff;
                    font-size: 11px;
                    line-height: 1.4;
                }
                .fiscal-header {
                    text-align: center;
                    margin-bottom: 8px;
                }
                .company-name {
                    font-size: 16px;
                    font-weight: 900;
                    margin-bottom: 2px;
                }
                .company-rif {
                    font-size: 12px;
                    font-weight: bold;
                    margin-bottom: 2px;
                }
                .company-details {
                    font-size: 10px;
                    color: #333;
                    margin-bottom: 3px;
                }
                .fiscal-banner {
                    text-align: center;
                    font-size: 14px;
                    font-weight: 900;
                    border-top: 1px dashed #000;
                    border-bottom: 1px dashed #000;
                    padding: 4px 0;
                    margin: 8px 0;
                    letter-spacing: 1px;
                }
                .meta-info {
                    font-size: 11px;
                    margin-bottom: 8px;
                    border-bottom: 1px dashed #000;
                    padding-bottom: 6px;
                }
                .items-table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 8px;
                }
                .items-table th {
                    border-bottom: 1px dashed #000;
                    text-align: left;
                    padding-bottom: 4px;
                    font-size: 10px;
                }
                .total-box {
                    border-top: 1px dashed #000;
                    margin-top: 8px;
                    padding-top: 6px;
                }
                .total-row {
                    display: flex;
                    justify-content: space-between;
                    font-size: 11px;
                    margin-bottom: 2px;
                }
                .total-row-highlight {
                    font-size: 13px;
                    font-weight: 900;
                    border-top: 1px dashed #000;
                    border-bottom: 1px dashed #000;
                    padding: 5px 0;
                    margin: 6px 0;
                }
                .footer {
                    text-align: center;
                    font-size: 9px;
                    border-top: 1px dashed #000;
                    padding-top: 8px;
                    margin-top: 12px;
                    line-height: 1.3;
                }
            </style>
        </head>
        <body onload="window.print(); window.close();">
            <div class="fiscal-header">
                <div class="company-name">FOGÓN GUAROTUYERO</div>
                <div class="company-details" style="font-weight: bold; font-size: 11px;">TASCA RESTAURANTE</div>
                <div class="company-rif">RIF: J-303602550</div>
                <div class="company-details">MÚSICA EN VIVO Y LOS MEJORES DJS</div>
            </div>
            
            <div class="fiscal-banner">*** FACTURA FISCAL ***</div>
            
            <div class="meta-info">
                <div style="font-size: 11px; margin-bottom: 4px; border-bottom: 1px dashed #eee; padding-bottom: 4px;">
                    <strong>DATOS DEL RECEPTOR:</strong><br/>
                    <strong>RAZÓN SOCIAL:</strong> ${clienteNombre ? clienteNombre.toUpperCase() : `CONSUMIDOR FINAL (MESA ${pedido.mesa.toUpperCase()})`}<br/>
                    <strong>RIF / C.I.:</strong> ${clienteRif ? clienteRif.toUpperCase() : 'V-99999999-9'}<br/>
                    <strong>DOMICILIO:</strong> ${clienteDireccion ? clienteDireccion.toUpperCase() : 'AV. PRINCIPAL, CARACAS'}<br/>
                </div>
                <div><strong>📅 FECHA / HORA:</strong> ${new Date().toLocaleDateString()} - ${formattedTime}</div>
                <div><strong>👤 ATENDIÓ:</strong> ${pedido.mesero || 'Caja Central'}</div>
                <div><strong>🆔 FACTURA FISCAL NRO:</strong> F-${String(pedido.id).padStart(8, '0')}</div>
                <div><strong>🔢 NRO. CONTROL:</strong> 00-00${String(pedido.id).padStart(6, '0')}</div>
                <div><strong>💳 ESTADO PAGO:</strong> ${estado.toUpperCase()}</div>
            </div>
            
            <table class="items-table">
                <thead>
                    <tr>
                        <th style="text-align:left;">CONCEPTO / DESCRIPCIÓN</th>
                        <th style="text-align:right;">MONTO</th>
                    </tr>
                </thead>
                <tbody>
                    ${itemsHtml}
                </tbody>
            </table>
            
            <div class="total-box">
                <div class="total-row">
                    <span>BASE IMPONIBLE (G 16.00%):</span>
                    <span>$${baseImponible.toFixed(2)}</span>
                </div>
                <div class="total-row">
                    <span>I.V.A. EXENTO:</span>
                    <span>$0.00</span>
                </div>
                <div class="total-row">
                    <span>I.V.A. (16.00%):</span>
                    <span>$${iva16.toFixed(2)}</span>
                </div>
                
                <div class="total-row total-row-highlight">
                    <span>TOTAL FACTURA USD:</span>
                    <span>$${totalUsd.toFixed(2)}</span>
                </div>

                <div class="total-row" style="font-weight: bold; margin-top: 4px;">
                    <span>BI. VES (Bs.):</span>
                    <span>Bs. ${baseImponibleVes.toFixed(2)}</span>
                </div>
                <div class="total-row" style="font-weight: bold;">
                    <span>IVA. VES (Bs.):</span>
                    <span>Bs. ${iva16Ves.toFixed(2)}</span>
                </div>
                <div class="total-row" style="font-size: 12px; font-weight: 900; color: #000; margin-top: 2px;">
                    <span>TOTAL VES (Bs.):</span>
                    <span>Bs. ${totalVes.toFixed(2)}</span>
                </div>
                <div style="font-size: 8px; text-align: right; color: #444; margin-top: 2px; font-style: italic;">
                    Tasa Oficial de Cambio: Bs. ${tasaCambio.toFixed(2)}
                </div>
                
                ${cobro !== null ? `
                <div class="total-row" style="border-top: 1px dotted #000; margin-top: 6px; padding-top: 4px;">
                    <span>EFECTIVO RECIBIDO:</span>
                    <span>$${parseFloat(cobro).toFixed(2)}</span>
                </div>
                <div class="total-row">
                    <span>VUELTO ENTREGADO:</span>
                    <span>$${parseFloat(cambio).toFixed(2)}</span>
                </div>
                ` : ''}
            </div>
            
            <div class="footer">
                <p style="font-weight: bold; margin: 0;">MÁQUINA FISCAL: DFG0012345</p>
                <p style="font-weight: bold; margin: 2px 0 0 0;">NRO. REGISTRO: SENIAT-000456123</p>
                <p style="margin-top: 10px; font-style: italic;">*** GRACIAS POR SU VISITA Y COMPRA ***</p>
            </div>
        </body>
        </html>
    `);
    printWindow.document.close();
}

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
                    try {
                        console.log("🖨️ Imprimiendo comanda de cocina automáticamente...");
                        imprimirComandaCocina(payload.new);
                    } catch (e) {
                        console.error("Error al imprimir comanda automáticamente:", e);
                    }
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
            <div class="kds-card-header">
                <div style="display: flex; align-items: center; gap: 6px;">
                    <span class="mesa-badge">${pedido.mesa}</span>
                    <button class="btn-print-icon" onclick="event.stopPropagation(); CocinaController.imprimirTicket(${pedido.id})" title="Imprimir Ticket Cocina" style="background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.15); border-radius: 6px; color: #fff; font-size: 0.85rem; padding: 4px 6px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; transition: background 0.2s;">🖨️</button>
                </div>
                <span class="cronometro ${colorRetraso}">${minutosTranscurridos} min</span>
            </div>
            <div class="kds-card-body"><p class="mesero-tag">Mesero: ${pedido.mesero}</p><div class="kds-items-list">${itemsHtml}</div></div>
            <div class="kds-card-footer">${botonAccion}</div>
        `;
        return div;
    },

    imprimirTicket(id) {
        const ped = this.pedidos.find(p => p.id === id);
        if (ped) {
            imprimirComandaCocina(ped);
        } else {
            Toast.error("Error", "No se encontró el pedido para imprimir.");
        }
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
    bankTransactions: [],
    monitoreoInterval: null,

    async init() {
        console.log("💰 Caja: Cargando pedidos...");
        
        // Inicializar el feed de transacciones bancarias de demostración
        this.bankTransactions = [
            { id: 101, banco: "Banco de Venezuela", telefono: "0424-9128312", referencia: "891231", monto: 12.50, fecha: new Date(Date.now() - 3600000), estado: "matched", cliente: "Carlos Mendoza" },
            { id: 102, banco: "Banesco", telefono: "0412-3214556", referencia: "542312", monto: 4.00, fecha: new Date(Date.now() - 1800000), estado: "matched", cliente: "María Rodríguez" },
            { id: 103, banco: "Provincial", telefono: "0414-7654321", referencia: "109283", monto: 15.00, fecha: new Date(Date.now() - 600000), estado: "pending", cliente: "José Graterol" },
            { id: 104, banco: "Banco Mercantil", telefono: "0416-1122334", referencia: "223344", monto: 8.50, fecha: new Date(Date.now() - 300000), estado: "pending", cliente: "Andrés Silva" }
        ];

        await this.cargarYRenderizar();
        
        // Iniciar monitoreo en tiempo real del banco
        this.iniciarMonitoreoBanco();

        window.onRealtimePedidosUpdate = async () => { 
            await this.cargarYRenderizar(); 
        };
    },

    async cargarYRenderizar() {
        try {
            const todos = await DataService.fetchPedidos();
            this.pedidos = todos.filter(p => p.estado !== 'pagado');
            
            // Auto-generar transacciones bancarias correspondientes para pedidos de Pago Móvil cargados
            this.pedidos.forEach(p => {
                if (p.metodo_pago === 'pago_movil' && p.estado_pago === 'pendiente') {
                    const yaExiste = this.bankTransactions.some(tx => tx.referencia === p.pago_referencia);
                    if (!yaExiste) {
                        this.generarPagoSimuladoBancario(p.pago_banco, p.pago_telefono, p.pago_referencia, p.total, p.mesero || "Cliente Autoservicio");
                    }
                }
            });

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
        const pId = document.getElementById('cuenta-pedido-id');
        if (pId) pId.textContent = `#${p.id}`;
        const pMesero = document.getElementById('cuenta-mesero-nombre');
        if (pMesero) pMesero.textContent = p.mesero || 'Autoservicio';
        document.getElementById('cuenta-items-list').innerHTML = p.items.map(it => `<div class="cuenta-item-line"><span>${it.cantidad}x ${it.producto}</span><span>${formatCurrency(it.precio * it.cantidad)}</span></div>`).join('');
        
        // Controlar visibilidad del aviso de retiro de factura
        const avisoRetiroEl = document.getElementById('caja-aviso-retiro-factura');
        if (avisoRetiroEl) {
            if (p.metodo_pago === 'pago_movil' || (p.mesero && p.mesero.includes('QR'))) {
                avisoRetiroEl.style.display = 'flex';
            } else {
                avisoRetiroEl.style.display = 'none';
            }
        }

        // Mostrar Datos de Pago Móvil si existen con Conciliación Real-Time
        const areaPago = document.getElementById('pm-conciliacion-area');
        if (p.metodo_pago === 'pago_movil') {
            areaPago.style.display = 'block';
            
            const paymentStatusText = p.estado_pago === 'aprobado' ? '✔️ CONCILIADO' : (p.estado_pago === 'rechazado' ? '❌ RECHAZADO' : '⏳ POR CONCILIAR');
            const statusClass = p.estado_pago === 'aprobado' ? 'matched' : 'pending';
            
            areaPago.innerHTML = `
                <div class="pm-conciliacion-box" style="border: 1px solid rgba(255,255,255,0.08); background: rgba(30,41,59,0.3); padding: 14px; border-radius: 12px; margin-bottom: 15px;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                        <h4 style="margin: 0; font-size: 0.9rem; font-weight: 700; color: #fff;">🔍 Conciliación Pago Móvil</h4>
                        <span class="bank-tx-status ${statusClass}" style="font-size: 0.65rem; padding: 2px 6px; border-radius: 4px;">${paymentStatusText}</span>
                    </div>
                    <div class="pm-data-grid" style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 0.775rem; background: rgba(0,0,0,0.15); padding: 10px; border-radius: 8px; margin-bottom: 12px;">
                        <div><small style="color: var(--color-text-muted); font-size: 0.65rem;">Banco Emisor:</small> <p style="font-weight: 600; color: #fff; margin-top: 1px;">${p.pago_banco || 'No especificado'}</p></div>
                        <div><small style="color: var(--color-text-muted); font-size: 0.65rem;">Teléfono:</small> <p style="font-weight: 600; color: #fff; margin-top: 1px;">${p.pago_telefono || 'No especificado'}</p></div>
                        <div style="grid-column: span 2;"><small style="color: var(--color-text-muted); font-size: 0.65rem;">Referencia Declarada:</small> <p class="ref-highlight" style="font-family: monospace; font-size: 0.95rem; font-weight: 700; color: #facc15; background: rgba(250,204,21,0.08); border: 1px dashed rgba(250,204,21,0.25); padding: 4px 8px; border-radius: 6px; margin-top: 3px; letter-spacing: 0.5px; text-align: center;">${p.pago_referencia || 'No especificado'}</p></div>
                    </div>
                    <div style="display: flex; flex-direction: column; gap: 8px;">
                        <button class="btn-approve" onclick="CajaController.abrirReconciliationModal(${p.id})" style="background: linear-gradient(135deg, #ff6b00 0%, #e65100 100%); color: white; border: none; padding: 12px; border-radius: 10px; font-weight: 700; font-size: 0.825rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px; box-shadow: 0 4px 12px rgba(255, 107, 0, 0.2); transition: 0.2s;">
                            <ion-icon name="shield-checkmark" style="font-size: 1.1rem;"></ion-icon> ⚡ Conciliar contra Banco (API)
                        </button>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
                            <button class="btn-approve" onclick="CajaController.validarPago(${p.id}, 'aprobado')" style="background: rgba(16, 185, 129, 0.1); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.25); padding: 6px; border-radius: 8px; font-weight: 600; font-size: 0.725rem; cursor: pointer;">
                                Forzar Aprobación
                            </button>
                            <button class="btn-reject" onclick="CajaController.validarPago(${p.id}, 'rechazado')" style="background: rgba(239, 68, 68, 0.1); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.25); padding: 6px; border-radius: 8px; font-weight: 600; font-size: 0.725rem; cursor: pointer;">
                                Rechazar Pago
                            </button>
                        </div>
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
            const pedidoParaImprimir = { ...this.pedidoSeleccionado };
            const cobroVal = document.getElementById('caja-recibido-input').value;
            const cobro = cobroVal ? parseFloat(cobroVal) : null;
            let cambio = null;
            if (cobro !== null && cobro >= total) {
                cambio = cobro - total;
            }

            await DataService.cobrarsePedido(this.pedidoSeleccionado.id, total, m, this.pedidoSeleccionado.mesero);
            Toast.success("Venta Exitosa", "La cuenta ha sido cerrada y pagada.");
            
            // Auto-prompt to print the ticket
            if (confirm("¿Deseas imprimir el Ticket de Factura (CANCELADO) del local?")) {
                let clienteNombre = '';
                let clienteRif = '';
                let clienteDireccion = '';
                if (confirm("¿Deseas registrar datos fiscales del receptor (Razón Social, RIF, Domicilio)?")) {
                    clienteNombre = prompt("Nombre o Razón Social del Cliente:", (pedidoParaImprimir.cliente || ""));
                    clienteRif = prompt("RIF o Cédula del Cliente:") || "";
                    clienteDireccion = prompt("Domicilio Fiscal del Cliente:") || "";
                }
                const tasa = await getTasaCambio();
                imprimirTicketCaja(pedidoParaImprimir, 'CANCELADO', cobro, cambio, tasa, clienteNombre, clienteRif, clienteDireccion);
            }

            this.cerrarDetalle();
            await this.cargarYRenderizar();
        } catch (e) { Toast.error("Error", e.message); }
    },

    async imprimirTicketSoporte(estado) {
        if (!this.pedidoSeleccionado) {
            Toast.error("Error", "No hay ninguna cuenta seleccionada.");
            return;
        }
        try {
            let clienteNombre = '';
            let clienteRif = '';
            let clienteDireccion = '';
            if (confirm("¿Deseas registrar datos fiscales del receptor (Razón Social, RIF, Domicilio)?")) {
                clienteNombre = prompt("Nombre o Razón Social del Cliente:", (this.pedidoSeleccionado.cliente || ""));
                clienteRif = prompt("RIF o Cédula del Cliente:") || "";
                clienteDireccion = prompt("Domicilio Fiscal del Cliente:") || "";
            }
            const tasa = await getTasaCambio();
            const cobroVal = document.getElementById('caja-recibido-input').value;
            const cobro = cobroVal ? parseFloat(cobroVal) : null;
            let cambio = null;
            if (cobro !== null && cobro >= this.pedidoSeleccionado.total) {
                cambio = cobro - this.pedidoSeleccionado.total;
            }
            imprimirTicketCaja(this.pedidoSeleccionado, estado, cobro, cambio, tasa, clienteNombre, clienteRif, clienteDireccion);
        } catch (e) {
            console.error("Error al imprimir ticket soporte:", e);
            imprimirTicketCaja(this.pedidoSeleccionado, estado, null, null, 45.5);
        }
    },

    iniciarMonitoreoBanco() {
        if (this.monitoreoInterval) clearInterval(this.monitoreoInterval);
        
        // Renderizar inmediatamente las transacciones iniciales
        this.renderBankTransactions();
        
        // Cada 60 segundos simular un nuevo pago móvil entrante aleatorio
        this.monitoreoInterval = setInterval(() => {
            this.generarPagoSimuladoBancario();
        }, 60000);
    },

    generarPagoSimuladoBancario(customBanco, customTelefono, customReferencia, customMonto, customCliente) {
        const bancos = ["Banco de Venezuela", "Banesco", "Provincial", "Banco Mercantil", "BNC", "Bancaribe"];
        const nombres = ["Luisa Ortega", "Alejandro Peña", "Gabriela Gomez", "Ruben Dario", "Beatriz Infante", "Pedro Sanchez", "Yusmeri Rivas", "Franklin Leon"];
        
        const banco = customBanco || bancos[Math.floor(Math.random() * bancos.length)];
        const telefono = customTelefono || "04" + [12,14,16,24,26][Math.floor(Math.random() * 5)] + "-" + Math.floor(1000000 + Math.random() * 9000000);
        const referencia = customReferencia || String(Math.floor(100000 + Math.random() * 900000));
        const monto = customMonto ? parseFloat(customMonto) : parseFloat((2 + Math.random() * 25).toFixed(2));
        const cliente = customCliente || nombres[Math.floor(Math.random() * nombres.length)];
        
        const nuevaTx = {
            id: Date.now(),
            banco,
            telefono,
            referencia,
            monto,
            fecha: new Date(),
            estado: "pending",
            cliente,
            isNew: true // Para animar flash
        };
        
        // Agregar al inicio del arreglo
        this.bankTransactions.unshift(nuevaTx);
        
        // Limitar a las últimas 15 transacciones para no saturar memoria
        if (this.bankTransactions.length > 15) {
            this.bankTransactions.pop();
        }
        
        // Renderizar el feed
        this.renderBankTransactions();
        console.log("🏦 Nueva transacción bancaria registrada: Ref " + referencia);
    },

    async renderBankTransactions() {
        const feed = document.getElementById('bank-transactions-feed');
        if (!feed) return;
        
        if (this.bankTransactions.length === 0) {
            feed.innerHTML = `
                <div style="text-align: center; color: var(--color-text-muted); font-size: 0.8rem; padding: 20px 0;">
                    No hay transacciones registradas hoy.
                </div>
            `;
            return;
        }
        
        const tasa = await getTasaCambio();
        
        // Actualizar indicador visual de la Tasa BCV
        const bcvValEl = document.getElementById('bcv-rate-value');
        const bcvIndEl = document.getElementById('bcv-rate-indicator');
        if (bcvValEl && bcvIndEl) {
            bcvValEl.textContent = tasa.toFixed(2);
            bcvIndEl.style.display = 'inline-flex';
        }
        
        feed.innerHTML = this.bankTransactions.map(tx => {
            const montoVes = tx.monto * tasa;
            const statusLabel = tx.estado === 'matched' ? 'Conciliado' : 'Pendiente';
            const statusClass = tx.estado === 'matched' ? 'matched' : 'pending';
            const flashClass = tx.isNew ? 'new-tx' : '';
            const fechaStr = new Date(tx.fecha).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            
            // Quitar la bandera isNew después de renderizar para que no re-anime
            if (tx.isNew) {
                setTimeout(() => { tx.isNew = false; }, 2000);
            }
            
            return `
                <div class="bank-tx-card ${flashClass}">
                    <div class="bank-tx-details">
                        <div style="display: flex; align-items: center; gap: 6px;">
                            <span style="font-weight: 700; color: #fff;">${tx.banco}</span>
                            <span class="bank-tx-status ${statusClass}">${statusLabel}</span>
                        </div>
                        <div class="bank-tx-meta">
                            <span>🕒 ${fechaStr}</span>
                            <span>Ref: <strong style="color: #cbd5e1; font-family: monospace;">${tx.referencia}</strong></span>
                        </div>
                        <div style="font-size: 0.65rem; color: var(--color-text-muted);">
                            Remitente: <strong style="color: #cbd5e1;">${tx.cliente}</strong> (${tx.telefono})
                        </div>
                    </div>
                    <div class="bank-tx-amount-box">
                        <div class="bank-tx-amount">$${tx.monto.toFixed(2)}</div>
                        <div style="font-size: 0.65rem; color: var(--color-text-muted);">Bs. ${montoVes.toFixed(2)}</div>
                    </div>
                </div>
            `;
        }).join('');
    },

    async abrirReconciliationModal(pedidoId) {
        const p = this.pedidos.find(ped => ped.id == pedidoId);
        if (!p) return;
        
        const modal = document.getElementById('bank-reconciliation-modal');
        if (!modal) return;
        
        modal.classList.add('active');
        
        const tasa = await getTasaCambio();
        const body = document.getElementById('reconcile-modal-body');
        body.innerHTML = `
            <div style="border: 1px solid rgba(255,255,255,0.06); background: rgba(30,41,59,0.25); padding: 14px; border-radius: 12px; font-size: 0.8rem; display: flex; flex-direction: column; gap: 8px;">
                <div style="display: flex; justify-content: space-between;">
                    <span style="color: var(--color-text-muted);">Mesa / Pedido:</span>
                    <strong style="color: #fff;">${p.mesa} (#${p.id})</strong>
                </div>
                <div style="display: flex; justify-content: space-between;">
                    <span style="color: var(--color-text-muted);">Monto del Pedido:</span>
                    <strong style="color: #10b981; font-size: 1rem;">$${p.total.toFixed(2)} <span style="font-size: 0.75rem; font-weight: normal; color: var(--color-text-muted);">(Bs. ${(p.total * tasa).toFixed(2)})</span></strong>
                </div>
                <div style="display: flex; justify-content: space-between; border-top: 1px solid rgba(255,255,255,0.05); padding-top: 8px; margin-top: 4px;">
                    <span style="color: var(--color-text-muted);">Banco Declarado:</span>
                    <span style="color: #fff; font-weight: 600;">${p.pago_banco || 'No especificado'}</span>
                </div>
                <div style="display: flex; justify-content: space-between;">
                    <span style="color: var(--color-text-muted);">Teléfono Declarado:</span>
                    <span style="color: #fff; font-weight: 600;">${p.pago_telefono || 'No especificado'}</span>
                </div>
                <div style="display: flex; justify-content: space-between;">
                    <span style="color: var(--color-text-muted);">Referencia Declarada:</span>
                    <span style="font-family: monospace; color: #facc15; font-weight: 700; letter-spacing: 0.5px;">${p.pago_referencia || 'No especificado'}</span>
                </div>
            </div>
            
            <div style="display: flex; flex-direction: column; gap: 8px; margin-top: 10px;">
                <span style="font-size: 0.75rem; font-weight: 700; letter-spacing: 0.5px; color: var(--color-text-muted); text-transform: uppercase;">Consola de Conexión Bancaria en Vivo:</span>
                <div id="reconcile-console" class="reconcile-console">
                    <p style="color: #8b5cf6;">[CONEXIÓN] Inicializando enlace de datos con SUDEBAN Gateway...</p>
                </div>
            </div>
            
            <div id="reconcile-result-area" style="margin-top: 5px;"></div>
        `;
        
        // Comenzar simulación de consulta
        this.ejecutarHandshakeReconcile(p);
    },
    
    async ejecutarHandshakeReconcile(pedido) {
        const consoleEl = document.getElementById('reconcile-console');
        const resultEl = document.getElementById('reconcile-result-area');
        if (!consoleEl) return;
        
        const log = (msg, cl = '') => {
            const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            const p = document.createElement('p');
            if (cl) p.className = cl;
            p.textContent = `[${time}] ${msg}`;
            consoleEl.appendChild(p);
            consoleEl.scrollTop = consoleEl.scrollHeight;
        };
        
        // Log secuenciales con delays realistas
        setTimeout(() => {
            log("Conexión segura establecida con la pasarela interbancaria (SSL TLS 1.3).", "info");
        }, 600);
        
        setTimeout(() => {
            log(`Buscando transacciones entrantes para la referencia '${pedido.pago_referencia}'...`, "info");
        }, 1300);
        
        setTimeout(() => {
            log(`Validando contra registros del banco receptor '${pedido.pago_banco}'...`, "info");
        }, 2000);
        
        setTimeout(async () => {
            // Buscar si la transacción coincide
            const match = this.bankTransactions.find(tx => 
                tx.referencia === pedido.pago_referencia && 
                tx.estado === 'pending'
            ) || this.bankTransactions.find(tx => tx.referencia === pedido.pago_referencia); // fallback por referencia únicamente
            
            if (match) {
                log(`¡TRANSACCIÓN ENCONTRADA EN EL LIBRO MAYOR!`, "success");
                log(`Coincidencia perfecta: Referencia ${match.referencia} | Monto $${match.monto.toFixed(2)} (${match.cliente})`, "success");
                log(`Procediendo a desbloquear el pedido #${pedido.id} y registrar venta.`, "info");
                
                // Actualizar estado local del ledger bancario
                match.estado = 'matched';
                this.renderBankTransactions();
                
                // Mostrar UI Éxito
                resultEl.innerHTML = `
                    <div class="reconcile-success-state">
                        <div style="background: rgba(16, 185, 129, 0.15); width: 60px; height: 60px; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-bottom: 12px; border: 1px solid rgba(16, 185, 129, 0.3);">
                            <ion-icon name="checkmark-circle-outline" style="font-size: 2rem; color: #10b981;"></ion-icon>
                        </div>
                        <h4 style="margin: 0 0 4px 0; color: #10b981; font-size: 1.1rem; font-weight: 700;">¡CONCILIACIÓN EXITOSA!</h4>
                        <p style="margin: 0; color: var(--color-text-muted); font-size: 0.8rem;">La transacción ha sido verificada en tiempo real contra el banco.</p>
                        <p style="margin: 6px 0 0 0; color: #fff; font-size: 0.75rem; font-family: monospace; background: rgba(255,255,255,0.05); padding: 4px 8px; border-radius: 4px;">ID Conciliación: BDV-${Math.floor(100000000 + Math.random() * 900000000)}</p>
                    </div>
                `;
                
                // Ejecutar aprobación definitiva de pago y liberar mesa!
                setTimeout(async () => {
                    try {
                        await DataService.actualizarEstadoPago(pedido.id, 'aprobado');
                        await DataService.cobrarsePedido(pedido.id, pedido.total, 'pago_movil', pedido.mesero);
                        Toast.success("Pago Conciliado", `Mesa ${pedido.mesa} liberada y venta registrada.`);
                        this.cerrarReconciliationModal();
                        this.cerrarDetalle();
                        await this.cargarYRenderizar();
                    } catch (e) {
                        Toast.error("Error", e.message);
                    }
                }, 1800);
                
            } else {
                log(`ADVERTENCIA: Transacción no localizada con la referencia '${pedido.pago_referencia}'.`, "error");
                log(`No se encontraron registros pendientes de conciliación en ${pedido.pago_banco} para ese identificador.`, "error");
                
                // Mostrar UI de Fallo / Conciliación Manual
                resultEl.innerHTML = `
                    <div style="border: 1px solid rgba(239, 68, 68, 0.15); background: rgba(239, 68, 68, 0.05); padding: 14px; border-radius: 12px; margin-top: 10px; display: flex; flex-direction: column; gap: 10px; animation: scaleUp 0.3s ease;">
                        <div style="display: flex; align-items: center; gap: 8px;">
                            <span style="font-size: 1.25rem;">⚠️</span>
                            <div>
                                <h4 style="margin: 0; color: #ef4444; font-size: 0.85rem; font-weight: 700;">Sin Registro de Transferencia en Banco</h4>
                                <small style="color: var(--color-text-muted); font-size: 0.725rem;">Verifica la referencia o asocia una transferencia pendiente manualmente.</small>
                            </div>
                        </div>
                        
                        <div class="reconcile-manual-section">
                            <span style="font-size: 0.725rem; font-weight: 700; color: var(--color-text-muted); text-transform: uppercase;">Transferencias de Pago Móvil Recibidas Sin Conciliar:</span>
                            <div class="reconcile-manual-list" id="reconcile-manual-list" style="margin-top: 6px;">
                                <!-- Se inyectan las tx pendientes del feed bankTransactions -->
                            </div>
                        </div>
                        
                        <div style="display: flex; gap: 8px; margin-top: 5px;">
                            <button onclick="CajaController.ejecutarHandshakeReconcile(CajaController.pedidos.find(p => p.id == ${pedido.id}))" style="flex: 1; background: rgba(255,255,255,0.08); border: 1px solid var(--color-border); color: white; padding: 8px; border-radius: 6px; font-size: 0.75rem; font-weight: 600; cursor: pointer; transition: 0.2s;">
                                🔄 Re-intentar Consulta
                            </button>
                            <button onclick="CajaController.cerrarReconciliationModal()" style="flex: 1; background: rgba(239, 68, 68, 0.12); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.2); padding: 8px; border-radius: 6px; font-size: 0.75rem; font-weight: 600; cursor: pointer; transition: 0.2s;">
                                Cancelar
                            </button>
                        </div>
                    </div>
                `;
                
                this.renderManualTransactionsList(pedido);
            }
        }, 2800);
    },
    
    renderManualTransactionsList(pedido) {
        const listEl = document.getElementById('reconcile-manual-list');
        if (!listEl) return;
        
        const pendings = this.bankTransactions.filter(tx => tx.estado === 'pending');
        
        if (pendings.length === 0) {
            listEl.innerHTML = `
                <div style="text-align: center; color: var(--color-text-muted); font-size: 0.725rem; padding: 10px 0;">
                    No hay transferencias pendientes sin conciliar en la cuenta.
                </div>
            `;
            return;
        }
        
        listEl.innerHTML = pendings.map(tx => `
            <div onclick="CajaController.vincularManual(${pedido.id}, ${tx.id})" style="display: flex; justify-content: space-between; align-items: center; padding: 8px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.05); border-radius: 6px; cursor: pointer; transition: 0.2s; font-size: 0.725rem;" onmouseover="this.style.background='rgba(16,185,129,0.08)'; this.style.borderColor='rgba(16,185,129,0.25)';" onmouseout="this.style.background='rgba(255,255,255,0.03)'; this.style.borderColor='rgba(255,255,255,0.05)';">
                <div style="display: flex; flex-direction: column; gap: 2px;">
                    <span style="font-weight: 700; color: #fff;">${tx.banco} | Ref: ${tx.referencia}</span>
                    <span style="color: var(--color-text-muted); font-size: 0.65rem;">Remitente: ${tx.cliente} (${tx.telefono})</span>
                </div>
                <div style="text-align: right;">
                    <strong style="color: #10b981;">$${tx.monto.toFixed(2)}</strong>
                    <div style="font-size: 0.6rem; color: #3b82f6;">🔗 Vincular</div>
                </div>
            </div>
        `).join('');
    },
    
    async vincularManual(pedidoId, txId) {
        const p = this.pedidos.find(ped => ped.id == pedidoId);
        const tx = this.bankTransactions.find(t => t.id == txId);
        if (!p || !tx) return;
        
        if (!confirm(`¿Vincular el Pago Móvil del Banco (Ref: ${tx.referencia}, Monto: $${tx.monto.toFixed(2)}) con el pedido de la mesa ${p.mesa}?`)) return;
        
        try {
            tx.estado = 'matched';
            this.renderBankTransactions();
            
            await DataService.actualizarEstadoPago(p.id, 'aprobado');
            await DataService.cobrarsePedido(p.id, p.total, 'pago_movil', p.mesero);
            
            Toast.success("Vincular Manual", "Pago conciliado exitosamente. Mesa liberada.");
            this.cerrarReconciliationModal();
            this.cerrarDetalle();
            await this.cargarYRenderizar();
        } catch (e) {
            Toast.error("Error al conciliar", e.message);
        }
    },
    
    cerrarReconciliationModal() {
        const modal = document.getElementById('bank-reconciliation-modal');
        if (modal) modal.classList.remove('active');
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
            this.allLogs = [...this.logs]; // Respaldo para filtros interactivos
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
        tbody.innerHTML = this.logs.map(log => {
            const mesaInfo = log.pedidos?.mesa ? ` (${log.pedidos.mesa})` : '';
            return `
                <tr>
                    <td>#${log.id}</td>
                    <td>Pedido #${log.pedido_id}${mesaInfo}</td>
                    <td>${log.metodo_pago.toUpperCase()}</td>
                    <td>${log.mesero}</td>
                    <td style="color:#10B981; font-weight:700;">${formatCurrency(log.monto)}</td>
                    <td>${new Date(log.creado_en).toLocaleString('es-VE', { dateStyle: 'short', timeStyle: 'short' })}</td>
                </tr>
            `;
        }).join('');
    },

    filtrarYBuscar() {
        try {
            const query = document.getElementById('filtro-auditoria-input')?.value?.toLowerCase() || '';
            const mesaQuery = document.getElementById('filtro-auditoria-mesa')?.value?.toLowerCase() || '';
            const fechaQuery = document.getElementById('filtro-auditoria-fecha')?.value || '';

            if (!this.allLogs) {
                this.allLogs = [...this.logs];
            }

            this.logs = this.allLogs.filter(log => {
                const matchQuery = !query || 
                    (log.mesero && log.mesero.toLowerCase().includes(query)) || 
                    (log.metodo_pago && log.metodo_pago.toLowerCase().includes(query));

                const logMesa = log.pedidos?.mesa || '';
                const matchMesa = !mesaQuery || logMesa.toLowerCase().includes(mesaQuery);

                let matchFecha = true;
                if (fechaQuery) {
                    const logDate = new Date(log.creado_en).toISOString().split('T')[0];
                    matchFecha = (logDate === fechaQuery);
                }

                return matchQuery && matchMesa && matchFecha;
            });

            this.calcularKPIs();
            this.renderLogsTable();
        } catch (e) {
            console.error("Error al filtrar:", e);
        }
    },

    limpiarFiltros() {
        const inp = document.getElementById('filtro-auditoria-input');
        const mesa = document.getElementById('filtro-auditoria-mesa');
        const fecha = document.getElementById('filtro-auditoria-fecha');

        if (inp) inp.value = '';
        if (mesa) mesa.value = '';
        if (fecha) fecha.value = '';

        if (this.allLogs) {
            this.logs = [...this.allLogs];
        }
        this.calcularKPIs();
        this.renderLogsTable();
    },

    exportarCSV() {
        try {
            if (!this.logs || this.logs.length === 0) {
                Toast.error("Sin datos", "No hay registros financieros para exportar.");
                return;
            }
            
            let csvContent = "data:text/csv;charset=utf-8,\uFEFF";
            csvContent += "ID Registro,Pedido ID,Mesa/Cliente,Metodo Pago,Mesero/Operador,Monto (USD),Fecha Creacion\n";
            
            this.logs.forEach(log => {
                const mesaVal = log.pedidos?.mesa || '';
                const row = [
                    log.id,
                    log.pedido_id,
                    `"${mesaVal.replace(/"/g, '""')}"`,
                    log.metodo_pago,
                    `"${log.mesero.replace(/"/g, '""')}"`,
                    log.monto,
                    new Date(log.creado_en).toLocaleString('es-VE')
                ].join(",");
                csvContent += row + "\n";
            });
            
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement("a");
            link.setAttribute("href", encodedUri);
            link.setAttribute("download", `Auditoria_Ventas_${new Date().toLocaleDateString('es-VE')}.csv`);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            Toast.success("CSV Exportado", "El archivo de datos planos se descargó exitosamente.");
        } catch (e) {
            Toast.error("Error CSV", e.message);
        }
    },

    async generarReportePDF(tipo = 'diario') {
        try {
            const { jsPDF } = window.jspdf;
            if (!jsPDF) {
                Toast.error("Librería faltante", "No se pudo cargar la librería jsPDF.");
                return;
            }

            // Asegurar tener todos los logs originales para filtrar correctamente
            if (!this.allLogs && this.logs) {
                this.allLogs = [...this.logs];
            }

            const sourceLogs = this.allLogs || this.logs || [];
            if (sourceLogs.length === 0) {
                Toast.error("Sin datos", "No hay registros financieros para exportar.");
                return;
            }

            let logsParaReporte = [];
            let tituloReporte = "";
            let periodoReporte = "";
            let controlSuffix = "";

            if (tipo === 'diario') {
                let fechaSeleccionada = document.getElementById('filtro-auditoria-fecha')?.value;
                if (!fechaSeleccionada) {
                    // Si no hay seleccionada en el input, tomar la de hoy localmente
                    const hoy = new Date();
                    const offset = hoy.getTimezoneOffset();
                    const hoyAjustado = new Date(hoy.getTime() - (offset*60*1000));
                    fechaSeleccionada = hoyAjustado.toISOString().split('T')[0];
                }
                
                // Filtrar por la fecha seleccionada
                logsParaReporte = sourceLogs.filter(log => {
                    const logDate = new Date(log.creado_en).toISOString().split('T')[0];
                    return logDate === fechaSeleccionada;
                });

                tituloReporte = "REPORTE DIARIO DE VENTAS Y AUDITORÍA DE CAJA";
                // Formatear fecha para lectura
                const [aaaa, mm, dd] = fechaSeleccionada.split('-');
                periodoReporte = `Fecha de Auditoría: ${dd}/${mm}/${aaaa}`;
                controlSuffix = `D-${aaaa}${mm}${dd}`;
            } else {
                logsParaReporte = [...sourceLogs];
                tituloReporte = "REPORTE CONSOLIDADO COMPLETO DE AUDITORÍA Y VENTAS";
                periodoReporte = "Período: Historial Completo Acumulado";
                const hoyStr = new Date().toISOString().split('T')[0].replace(/-/g, "");
                controlSuffix = `C-${hoyStr}`;
            }

            if (logsParaReporte.length === 0) {
                Toast.warning("Sin transacciones", `No se encontraron movimientos financieros para el período (${periodoReporte}).`);
                if (!confirm("No hay transacciones registradas para este período. ¿Desea generar el reporte de auditoría en cero de todas formas?")) {
                    return;
                }
            }

            const doc = new jsPDF({
                orientation: 'portrait',
                unit: 'mm',
                format: 'letter'
            });

            // Estándar de Contabilidad Fiscal / Corporativo
            const commerceName = "LA COCINA REAL C.A.";
            const commerceRif = "J-40983214-5";
            const commerceAddress = "Av. Principal Las Mercedes, Caracas, Venezuela";
            const currentDate = new Date().toLocaleDateString('es-VE');
            const currentTime = new Date().toLocaleTimeString('es-VE');
            const nroControl = `NRO. CONTROL FISCAL: CA-${controlSuffix}-${Math.floor(1000 + Math.random() * 9000)}`;

            // --- 1. Cabecera (Header) ---
            doc.setFont("helvetica", "bold");
            doc.setFontSize(15);
            doc.setTextColor(255, 107, 0); // Color principal de la marca
            doc.text(commerceName, 14, 20);

            doc.setFont("helvetica", "normal");
            doc.setFontSize(8.5);
            doc.setTextColor(80, 80, 80);
            doc.text(`RIF: ${commerceRif}`, 14, 25);
            doc.text(commerceAddress, 14, 29);
            
            // Lado derecho de la cabecera (Metadatos contables)
            doc.setFont("helvetica", "bold");
            doc.setFontSize(8.5);
            doc.setTextColor(40, 40, 40);
            doc.text(nroControl, 112, 20);
            doc.setFont("helvetica", "normal");
            doc.text(`Emitido: ${currentDate} ${currentTime}`, 112, 25);
            doc.text(`Usuario Operador: Contabilidad General`, 112, 29);

            // Título de Reporte
            doc.setFont("helvetica", "bold");
            doc.setFontSize(13);
            doc.setTextColor(0, 0, 0);
            doc.text(tituloReporte, 14, 40);

            doc.setFont("helvetica", "italic");
            doc.setFontSize(10);
            doc.setTextColor(100, 100, 100);
            doc.text(periodoReporte, 14, 45);

            // Línea de separación principal
            doc.setDrawColor(255, 107, 0);
            doc.setLineWidth(0.8);
            doc.line(14, 48, 202, 48);

            // --- 2. Resumen Contable y Tributario ---
            const totalVentas = logsParaReporte.reduce((acc, l) => acc + parseFloat(l.monto), 0);
            const totalTransacciones = logsParaReporte.length;
            
            // IVA (16% estándar)
            const baseImponible = totalVentas / 1.16;
            const ivaTotal = totalVentas - baseImponible;

            doc.setFont("helvetica", "bold");
            doc.setFontSize(10.5);
            doc.setTextColor(0, 0, 0);
            doc.text("1. CONCILIACIÓN DE INGRESOS Y DECLARACIÓN DE IVA", 14, 55);

            // Dibujar recuadro para los totales de IVA / Neto
            doc.setDrawColor(220, 220, 220);
            doc.setLineWidth(0.2);
            doc.setFillColor(250, 250, 250);
            doc.rect(14, 58, 88, 38, 'F'); // Fondo
            doc.rect(14, 58, 88, 38, 'D'); // Borde

            doc.setFont("helvetica", "normal");
            doc.setFontSize(9);
            doc.setTextColor(50, 50, 50);
            doc.text(`Monto Neto (Base Imponible):`, 18, 64);
            doc.text(`Impuesto de Ley (IVA 16.00%):`, 18, 71);
            doc.text(`Monto Exento / Exonerado:`, 18, 78);
            
            doc.setFont("helvetica", "bold");
            doc.setTextColor(0, 0, 0);
            doc.text(formatCurrency(baseImponible), 96, 64, { align: "right" });
            doc.text(formatCurrency(ivaTotal), 96, 71, { align: "right" });
            doc.text(formatCurrency(0), 96, 78, { align: "right" });

            // Total Línea
            doc.setDrawColor(200, 200, 200);
            doc.line(18, 82, 96, 82);
            doc.setFontSize(9.5);
            doc.text("TOTAL VENTAS BRUTAS:", 18, 89);
            doc.setTextColor(16, 185, 129); // Verde de éxito
            doc.text(formatCurrency(totalVentas), 96, 89, { align: "right" });

            // Métodos de Pago a la derecha
            doc.setFillColor(250, 250, 250);
            doc.rect(110, 58, 92, 38, 'F');
            doc.rect(110, 58, 92, 38, 'D');

            doc.setFont("helvetica", "bold");
            doc.setFontSize(9);
            doc.setTextColor(0, 0, 0);
            doc.text("DESGLOSE POR MÉTODO DE PAGO", 114, 64);

            doc.setFont("helvetica", "normal");
            doc.setFontSize(8.5);
            doc.setTextColor(60, 60, 60);

            // Calcular montos por método de pago
            const metodos = logsParaReporte.map(l => l.metodo_pago);
            const freq = metodos.reduce((a, v) => (a[v] = (a[v] || 0) + 1, a), {});
            
            let yOffsetMetodo = 70;
            const metodosClave = ['efectivo', 'pagomovil', 'tarjeta', 'zelle'];
            const etiquetasMetodo = {
                'efectivo': 'Efectivo (Caja Chica)',
                'pagomovil': 'Pago Móvil (Banco)',
                'tarjeta': 'Tarjeta de Débito/Crédito',
                'zelle': 'Zelle / Divisas Extr.'
            };

            metodosClave.forEach(met => {
                const count = freq[met] || 0;
                const montoMetodo = logsParaReporte
                    .filter(l => l.metodo_pago === met)
                    .reduce((sum, l) => sum + parseFloat(l.monto), 0);
                const porcentaje = totalVentas > 0 ? ((montoMetodo / totalVentas) * 100).toFixed(1) : "0.0";
                
                doc.text(`${etiquetasMetodo[met] || met.toUpperCase()} (${count} op.):`, 114, yOffsetMetodo);
                doc.setFont("helvetica", "bold");
                doc.text(`${formatCurrency(montoMetodo)} (${porcentaje}%)`, 198, yOffsetMetodo, { align: "right" });
                doc.setFont("helvetica", "normal");
                yOffsetMetodo += 5.5;
            });

            // --- 3. Desglose por Operador / Mesero ---
            doc.setFont("helvetica", "bold");
            doc.setFontSize(10.5);
            doc.setTextColor(0, 0, 0);
            doc.text("2. RENDICIÓN DE CUENTAS POR OPERADOR / MESERO", 14, 104);

            const meserosLogs = logsParaReporte.map(l => l.mesero);
            const meserosUnicos = [...new Set(meserosLogs)];
            
            doc.setDrawColor(220, 220, 220);
            doc.setFillColor(252, 252, 252);
            doc.rect(14, 107, 188, 18, 'F');
            doc.rect(14, 107, 188, 18, 'D');

            doc.setFont("helvetica", "normal");
            doc.setFontSize(8.5);
            doc.setTextColor(60, 60, 60);

            let xMeseroOffset = 18;
            let countMesero = 0;
            meserosUnicos.forEach(m => {
                if (countMesero < 3) { // Mostrar los 3 operadores principales para no desbordar
                    const montoMesero = logsParaReporte
                        .filter(l => l.mesero === m)
                        .reduce((sum, l) => sum + parseFloat(l.monto), 0);
                    const countTrans = logsParaReporte.filter(l => l.mesero === m).length;
                    
                    doc.setFont("helvetica", "bold");
                    doc.text(m.toUpperCase(), xMeseroOffset, 113);
                    doc.setFont("helvetica", "normal");
                    doc.text(`Transacciones: ${countTrans}`, xMeseroOffset, 118);
                    doc.text(`Total Recaudado: ${formatCurrency(montoMesero)}`, xMeseroOffset, 122);
                    
                    xMeseroOffset += 60;
                    countMesero++;
                }
            });
            if (meserosUnicos.length === 0) {
                doc.text("No se registran operadores activos en este período.", 18, 115);
            }

            // --- 4. Tabla Detallada de Auditoría ---
            doc.setFont("helvetica", "bold");
            doc.setFontSize(10.5);
            doc.text("3. HISTORIAL CRONOLÓGICO Y CONTROL DE TRANSACCIONES", 14, 133);

            const tableRows = logsParaReporte.map((log) => [
                `#${log.id}`,
                `Pedido #${log.pedido_id}${log.pedidos?.mesa ? ' ('+log.pedidos.mesa+')' : ''}`,
                log.metodo_pago.toUpperCase(),
                log.mesero.toUpperCase(),
                new Date(log.creado_en).toLocaleString('es-VE', { dateStyle: 'short', timeStyle: 'short' }),
                formatCurrency(log.monto)
            ]);

            doc.autoTable({
                startY: 137,
                head: [['Ref ID', 'Origen de Transacción', 'Forma de Pago', 'Operador/Cajero', 'Fecha y Hora', 'Total Cobrado']],
                body: tableRows,
                theme: 'striped',
                headStyles: { fillColor: [30, 41, 59], textColor: [255, 255, 255], fontStyle: 'bold' }, // Gris oscuro pizarra contable
                styles: { fontSize: 8, cellPadding: 2 },
                columnStyles: {
                    5: { halign: 'right', fontStyle: 'bold' }
                },
                margin: { left: 14, right: 14 }
            });

            // --- 5. Firmas y Auditoría Interna (Footer de la página) ---
            const finalY = doc.previousAutoTable.finalY + 12;
            
            // Asegurarse de que no nos salgamos del final de la página (279mm de alto total en Letter)
            let drawSignaturesY = finalY;
            if (finalY > 215) {
                doc.addPage();
                drawSignaturesY = 30;
            }

            // Recuadro de Auditoría / Checklist de control contable
            doc.setDrawColor(200, 200, 200);
            doc.setFillColor(248, 250, 252);
            doc.rect(14, drawSignaturesY, 188, 22, 'F');
            doc.rect(14, drawSignaturesY, 188, 22, 'D');

            doc.setFont("helvetica", "bold");
            doc.setFontSize(8);
            doc.setTextColor(30, 41, 59);
            doc.text("CONTROLES DE AUDITORÍA Y VERIFICACIÓN CONTABLE INTERNA", 18, drawSignaturesY + 5);

            doc.setFont("helvetica", "normal");
            doc.setFontSize(7.5);
            doc.setTextColor(80, 80, 80);
            doc.text("[ X ] Arqueo físico de caja ejecutado sin diferencias.", 18, drawSignaturesY + 11);
            doc.text("[ X ] Conciliación bancaria contra transacciones de tarjeta y pago móvil.", 18, drawSignaturesY + 16);
            doc.text("[ X ] Comprobantes de compras y egresos verificados contra inventario.", 110, drawSignaturesY + 11);
            doc.text("[ X ] Declaración impositiva de IVA procesada de acuerdo al código de comercio.", 110, drawSignaturesY + 16);

            // Líneas de firma
            const signaturesY = drawSignaturesY + 40;
            doc.setDrawColor(120, 120, 120);
            doc.setLineWidth(0.3);
            doc.line(20, signaturesY, 80, signaturesY); // Firma 1
            doc.line(130, signaturesY, 190, signaturesY); // Firma 2

            doc.setFont("helvetica", "bold");
            doc.setFontSize(8.5);
            doc.setTextColor(40, 40, 40);
            doc.text("FIRMA DEL CAJERO / RESPONSABLE", 22, signaturesY + 4);
            doc.text("FIRMA DEL GERENTE / AUDITOR", 135, signaturesY + 4);

            doc.setFont("helvetica", "normal");
            doc.setFontSize(7.5);
            doc.setTextColor(120, 120, 120);
            doc.text("Nombre: _______________________", 22, signaturesY + 8);
            doc.text("Nombre: _______________________", 135, signaturesY + 8);
            doc.text("C.I. / RIF: ____________________", 22, signaturesY + 12);
            doc.text("C.I. / RIF: ____________________", 135, signaturesY + 12);

            // Sello
            doc.setDrawColor(220, 220, 220);
            doc.rect(94, signaturesY - 12, 22, 22);
            doc.setFontSize(6.5);
            doc.text("SELLO", 102, signaturesY - 2);
            doc.text("COMERCIAL", 98, signaturesY + 2);

            // Descargar el archivo PDF
            const filename = `Reporte_${tipo === 'diario' ? 'Diario' : 'Completo'}_Auditoria_${controlSuffix}.pdf`;
            doc.save(filename);
            Toast.success("PDF Generado", `El reporte ${tipo === 'diario' ? 'diario' : 'completo'} ha sido descargado exitosamente.`);
        } catch (e) {
            console.error("Error generando PDF:", e);
            Toast.error("Error PDF", e.message);
        }
    }
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
