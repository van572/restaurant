/**
 * LOGICA DEL CLIENTE - AUTOGESTIÓN POR QR
 * Permite al comensal ver el menú, armar su carrito y enviar el pedido.
 */

const MENU_DEFAULT_CLIENTE = [
    { nombre: "Hamburguesa Premium", precio: 12.50, categoria: "COMIDA", descripcion: "Queso cheddar, tocino, aderezo gourmet.", emoji: "🍔" },
    { nombre: "Pizza Personal Pepperoni", precio: 15.00, categoria: "COMIDA", descripcion: "Salsa de la casa, pepperoni, mozzarella.", emoji: "🍕" },
    { nombre: "Tacos de Res (x3)", precio: 8.50, categoria: "COMIDA", descripcion: "Cebollitas asadas, cilantro, salsas.", emoji: "🌮" },
    { nombre: "Parrilla Familiar (al Peso)", precio: 24.00, categoria: "COMIDA", descripcion: "Exquisito surtido de carnes premium cocidas a la brasa, servido por kilo o gramo.", emoji: "🥩" },
    { nombre: "Chicharrón Crujiente (al Peso)", precio: 18.00, categoria: "COMIDA", descripcion: "Tradicional chicharrón de cerdo bien crujiente con arepitas, servido por kilo o gramo.", emoji: "🥓" },
    { nombre: "Costillas de Cerdo (al Peso)", precio: 21.00, categoria: "COMIDA", descripcion: "Costillas de cerdo ahumadas con glaseado especial BBQ de la casa, servidas por kilo o gramo.", emoji: "🍖" },
    { nombre: "Alitas BBQ", precio: 9.50, categoria: "COMIDA", descripcion: "10 piezas de alitas bañadas en salsa barbacoa.", emoji: "🍗" },
    { nombre: "Papas Fritas", precio: 4.00, categoria: "ACOMPANAMIENTO", descripcion: "Doraditas y crujientes con sal marina.", emoji: "🍟" },
    { nombre: "Té Frío Limón", precio: 3.00, categoria: "BEBIDA", descripcion: "Infusión de té negro con zumo fresco.", emoji: "🍹" },
    { nombre: "Refresco Sabor Cola", precio: 2.50, categoria: "BEBIDA", descripcion: "Vaso grande con hielo y limón.", emoji: "🥤" },
    { nombre: "Agua Mineral", precio: 2.00, categoria: "BEBIDA", descripcion: "Agua gasificada purificada fría.", emoji: "💧" }
];

let state = {
    mesa: null,
    menu: [],
    carrito: [],
    isSending: false,
    tasaCambio: 45.5
};

// 1. INICIALIZACIÓN
document.addEventListener('DOMContentLoaded', () => {
    // Esperar a que DataService y Supabase estén listos
    const checkReady = setInterval(() => {
        if (typeof DataService !== 'undefined') {
            clearInterval(checkReady);
            initApp();
        }
    }, 50);
    
    // Timeout de seguridad por si algo falla
    setTimeout(() => clearInterval(checkReady), 5000);
});

async function initApp() {
    // Cargar tasa de cambio oficial del BCV antes de renderizar nada
    try {
        const tasa = await DataService.getSettings("tasa_cambio");
        state.tasaCambio = parseFloat(tasa) || 45.5;
        console.log("📊 Tasa BCV cargada en cliente:", state.tasaCambio);
    } catch (e) {
        console.warn("No se pudo obtener la tasa_cambio en el cliente, usando default de 45.5", e);
        state.tasaCambio = 45.5;
    }

    // Capturar mesa o alias de la URL
    const urlParams = new URLSearchParams(window.location.search);
    const mesaParam = urlParams.get('mesa');
    const aliasParam = urlParams.get('alias') || urlParams.get('cliente');
    
    const savedAlias = sessionStorage.getItem('cliente_alias') || sessionStorage.getItem('cliente_nombre');
    
    if (aliasParam) {
        state.alias = aliasParam;
        state.mesa = aliasParam;
        sessionStorage.setItem('cliente_alias', aliasParam);
        sessionStorage.setItem('cliente_nombre', aliasParam);
    } else if (mesaParam && !savedAlias) {
        // Soporte retrocompatible para QRs con mesa=X
        const legacyVal = `Mesa ${mesaParam}`;
        state.alias = legacyVal;
        state.mesa = legacyVal;
        sessionStorage.setItem('cliente_alias', legacyVal);
        sessionStorage.setItem('cliente_nombre', legacyVal);
    } else if (savedAlias) {
        state.alias = savedAlias;
        state.mesa = savedAlias;
    } else {
        state.alias = "";
        state.mesa = "";
    }

    actualizarUIIdentidad();

    // Cargar Menú
    await cargarMenu();

    // Cargar datos de Pago Movil
    cargarAjustesPago();

    // Sincronizar conexión Supabase UI
    const conn = await DataService.checkConnection();
    console.log("Supabase Connection Status:", conn.message);

    // Suscribirse a cambios en tiempo real del menú
    DataService.suscribirAMenu(() => {
        console.log("Sincronizando menú por actualización en tiempo real...");
        cargarMenu();
    });

    // Restaurar pedido activo si existe
    const activeOrderId = sessionStorage.getItem('active_order_id');
    if (activeOrderId) {
        state.activeOrderId = activeOrderId;
        startTrackingOrder(activeOrderId);
    }

    // Auto open modal on load if no alias exists to prompt identification
    if (!state.alias) {
        setTimeout(() => {
            openMesaModal();
        }, 800);
    }
}

function actualizarUIIdentidad() {
    const textSpan = document.getElementById('identity-text');
    if (textSpan) {
        textSpan.textContent = state.alias ? state.alias : "Identificarse";
    }
    const aliasInput = document.getElementById('cliente-alias-input');
    if (aliasInput) {
        aliasInput.value = state.alias || "";
    }
    const cartInput = document.getElementById('cliente-nombre-input');
    if (cartInput) {
        cartInput.value = state.alias || "";
    }
}

function openMesaModal() {
    const modal = document.getElementById('mesa-modal');
    if (modal) {
        modal.style.display = 'flex';
        const aliasInput = document.getElementById('cliente-alias-input');
        if (aliasInput) {
            aliasInput.value = state.alias || "";
            setTimeout(() => aliasInput.focus(), 150);
        }
    }
}

function closeMesaModal() {
    const modal = document.getElementById('mesa-modal');
    if (modal) modal.style.display = 'none';
}

function guardarIdentidadCliente() {
    const input = document.getElementById('cliente-alias-input');
    const val = input ? input.value.trim() : "";
    if (!val) {
        Toast.warning("Nombre requerido 🗣️", "Por favor ingresa tu nombre o alias para continuar.");
        if (input) input.focus();
        return;
    }
    
    state.alias = val;
    state.mesa = val;
    sessionStorage.setItem('cliente_alias', val);
    sessionStorage.setItem('cliente_nombre', val);
    
    actualizarUIIdentidad();
    closeMesaModal();
    Toast.success("Identificado", `¡Hola, ${val}! Ya puedes armar tu comanda.`);
}

function showQRError() {
    const content = document.getElementById('main-content');
    content.innerHTML = `
        <div class="qr-alert">
            <ion-icon name="person-circle-outline" style="font-size: 3rem; margin-bottom: 12px;"></ion-icon>
            <p><strong>Identificación Requerida 🗣️</strong></p>
            <p>Por favor, selecciona tu nombre o alias haciendo clic en el botón "Identificarse" en la esquina superior.</p>
        </div>
    `;
    document.getElementById('cart-bar').style.display = 'none';
}

async function cargarMenu() {
    try {
        if (!DataService.isReal()) {
            throw new Error("CONFIG_MISSING");
        }

        let menuData = null;
        try {
            menuData = await DataService.fetchMenu();
        } catch (fetchErr) {
            console.warn("⚠️ Error obteniendo menú directo de Supabase, activando fallback local:", fetchErr);
            const msg = fetchErr.message || String(fetchErr);
            if (msg.includes("schema cache") || msg.includes("does not exist") || msg.includes("relation") || msg.includes("404") || msg.includes("400")) {
                state.supabaseSchemaError = msg;
                state.menu = MENU_DEFAULT_CLIENTE;
                renderMenu();
                return;
            } else {
                throw fetchErr;
            }
        }
        
        if (!menuData || menuData.length === 0) {
            console.warn("La tabla de menú en Supabase está vacía. Cargando menú de demostración.");
            state.menu = MENU_DEFAULT_CLIENTE;
            state.supabaseSchemaError = "La tabla 'menu' existe pero está vacía. Mostrando lista por defecto.";
            renderMenu();
            return;
        }
        
        state.menu = menuData;
        renderMenu();
    } catch (e) {
        console.group("🚨 Error al cargar el sistema (Cliente)");
        console.error("Tipo de error:", e.message);
        console.error("Detalles:", e);
        console.groupEnd();
        
        const container = document.getElementById('main-content');
        let errorTitle = "Error de Conexión";
        let errorDesc = "No se pudo obtener el menú del restaurante. Por favor, verifica tu conexión a internet o contacta al personal.";
        let icon = "cloud-offline-outline";

        if (e.message === "CONFIG_MISSING") {
            errorTitle = "Configuración no detectada";
            errorDesc = "Este sistema requiere una configuración de Supabase. Asegúrate de escanear un código QR válido generado por el personal de caja.";
            icon = "settings-outline";
        }

        container.innerHTML = `
            <div class="qr-alert" style="border-color: var(--danger);">
                <ion-icon name="${icon}" style="font-size: 3rem; color: var(--danger); margin-bottom: 12px;"></ion-icon>
                <p><strong>${errorTitle}</strong></p>
                <p>${errorDesc}</p>
                <div style="margin-top: 15px; font-size: 0.7rem; color: #94A3B8; background: rgba(0,0,0,0.05); padding: 8px; border-radius: 4px;">
                    Info técnica: ${e.message || 'Error desconocido'}
                </div>
                <button class="confirm-btn" style="margin-top: 20px;" onclick="location.reload()">Reintentar</button>
            </div>
        `;
    }
}

async function cargarAjustesPago() {
    try {
        const pm = await DataService.getSettings('pago_movil');
        if (pm) {
            document.getElementById('pm-info-banco').textContent = pm.banco;
            document.getElementById('pm-info-rif').textContent = `RIF: ${pm.rif}`;
            document.getElementById('pm-info-telefono').textContent = pm.telefono;
        }
    } catch (e) {
        console.error("No se pudieron cargar los datos de pago:", e);
        // Fallback or leave defaults
    }
}

// 2. RENDERIZACIÓN
function renderMenu() {
    const container = document.getElementById('main-content');
    container.innerHTML = '';
    
    // Si hay un error de esquema o tabla inexistente de Supabase, mostramos un banner informativo didáctico arriba
    if (state.supabaseSchemaError) {
        const errorBanner = document.createElement('div');
        errorBanner.className = 'schema-warning-banner';
        errorBanner.style.cssText = `
            background: #FFFBEB;
            color: #78350F;
            border: 1px solid #FDE68A;
            border-radius: var(--radius);
            padding: 16px;
            margin-bottom: 24px;
            font-size: 0.85rem;
            line-height: 1.5;
            display: flex;
            flex-direction: column;
            gap: 12px;
            box-shadow: var(--shadow);
            text-align: left;
        `;
        errorBanner.innerHTML = `
            <div style="display:flex; align-items:flex-start; gap:10px;">
                <ion-icon name="warning-outline" style="font-size: 1.6rem; color: #D97706; flex-shrink: 0;"></ion-icon>
                <div>
                    <strong style="display:block; margin-bottom:4px; font-size:0.95rem; color: #92400E;">⚠️ Base de datos incompleta en Supabase</strong>
                    <span>Tu base de datos de Supabase está enlazada pero le faltan tablas del sistema (como <strong>'public.menu'</strong>). Hemos activado el <strong>Menú Local de Emergencia</strong> para que puedas seguir probando todo el flujo de pedidos de inmediato.</span>
                </div>
            </div>
            <div style="background: rgba(251, 191, 36, 0.1); border-radius: 12px; padding: 12px; font-size: 0.8rem;">
                <p style="font-weight:700; margin-bottom: 6px; color: #92400E; display: flex; align-items: center; gap: 4px;">
                    <ion-icon name="code-working-outline"></ion-icon> Solución en 1 minuto:
                </p>
                <ol style="margin-left: 18px; display: flex; flex-direction: column; gap: 4px;">
                    <li>Entra a la consola de <a href="https://supabase.com" target="_blank" style="color:var(--primary); font-weight:700; text-decoration:underline;">Supabase</a>.</li>
                    <li>Ve a la pestaña <strong>"SQL Editor"</strong> (menú izquierdo) y pulsa <strong>"Create query" / "New Query"</strong>.</li>
                    <li>Abre el archivo <strong style="font-family:monospace; background:rgba(0,0,0,0.05); padding:2px 4px; border-radius:3px;">database/esquema.sql</strong> de tu proyecto, copia su contenido completo y pégalo allí.</li>
                    <li>Presiona el botón <strong style="color:#059669;">"Run"</strong> para generar todas las tablas e inicializar los platos de una sola vez.</li>
                </ol>
            </div>
            <div style="font-size: 0.7rem; opacity: 0.7; font-family: monospace; border-top: 1px solid rgba(0,0,0,0.05); padding-top: 6px;">
                Error: ${state.supabaseSchemaError}
            </div>
        `;
        container.appendChild(errorBanner);
    }

    if (state.menu.length === 0) {
        container.innerHTML = '<p style="text-align:center;">No hay productos disponibles por ahora.</p>';
        return;
    }

    // Agrupar por Categoría
    const categorias = [...new Set(state.menu.map(p => p.categoria))];
    let cardIndex = 0;
    
    categorias.forEach(cat => {
        const section = document.createElement('section');
        section.className = 'categoria-section';
        
        const products = state.menu.filter(p => p.categoria === cat);
        if (products.length === 0) return;

        const title = document.createElement('h2');
        title.className = 'categoria-title';
        title.innerHTML = `<span>${getCategoryEmoji(cat)}</span> ${cat}`;
        section.appendChild(title);
        
        const grid = document.createElement('div');
        grid.className = 'menu-grid';
        
        products.forEach(prod => {
            const isAvailable = prod.disponible !== false;
            const card = document.createElement('div');
            card.className = `platillo-card ${isAvailable ? '' : 'agotado'}`;
            card.style.animationDelay = (cardIndex * 0.08) + 's';
            cardIndex++;
            
            if (!isAvailable) {
                card.style.opacity = '0.6';
                card.style.filter = 'grayscale(0.8)';
            }
            
            const localImg = localStorage.getItem(`menu_img_${prod.id}`) || localStorage.getItem(`menu_img_${prod.nombre}`);
            const imgToUse = prod.imagen || localImg;
            const imageHtml = imgToUse 
                ? `<img src="${imgToUse}" style="width: 100%; height: 100%; object-fit: cover; border-radius: 18px;" onerror="this.style.display='none'" />` 
                : (prod.emoji || '🍽️');
            
            card.innerHTML = `
                <div class="platillo-emoji" style="overflow: hidden; padding: 0; display: flex; align-items: center; justify-content: center;">${imageHtml}</div>
                <div class="platillo-info">
                    <div class="platillo-name">${prod.nombre} ${isAvailable ? '' : '<span style="font-size:0.6rem; color:var(--danger);">(AGOTADO)</span>'}</div>
                    <div class="platillo-desc">${prod.descripcion || ''}</div>
                    <div class="platillo-footer">
                        <div class="platillo-price" style="display: flex; flex-direction: column;">
                            <span style="font-size: 1.1rem; font-weight: 800; color: var(--primary);">$${parseFloat(prod.precio).toFixed(2)}</span>
                            <span style="font-size: 0.72rem; color: var(--text-muted); font-weight: normal; margin-top: 2px;">Bs. ${(parseFloat(prod.precio) * state.tasaCambio).toFixed(2)}</span>
                        </div>
                        <button class="add-btn" ${isAvailable ? '' : 'disabled'} onclick="addToCart('${prod.nombre}', ${prod.precio})">
                            <ion-icon name="${isAvailable ? 'add' : 'ban-outline'}"></ion-icon>
                        </button>
                    </div>
                </div>
            `;
            grid.appendChild(card);
        });
        
        section.appendChild(grid);
        container.appendChild(section);
    });
}

function getCategoryEmoji(cat) {
    switch(cat.toUpperCase()) {
        case 'COMIDA': return '🍔';
        case 'BEBIDA': return '🥤';
        case 'ACOMPANAMIENTO': return '🍟';
        case 'POSTRE': return '🍰';
        default: return '🍴';
    }
}

// 3. GESTIÓN DEL CARRITO
function addToCart(nombre, precio) {
    const index = state.carrito.findIndex(item => item.producto === nombre);
    
    if (index !== -1) {
        state.carrito[index].cantidad += 1;
    } else {
        state.carrito.push({
            producto: nombre,
            cantidad: 1,
            precio: precio,
            notas: ""
        });
    }
    
    actualizarBarraCarrito();
    // Feedback visual
    Toast.info("Añadido", `${nombre} al pedido.`);
}

function actualizarBarraCarrito() {
    const bar = document.getElementById('cart-bar');
    const countEl = bar.querySelector('.cart-count');
    const totalEl = bar.querySelector('.cart-total');
    
    const totalItems = state.carrito.reduce((acc, item) => acc + item.cantidad, 0);
    const totalPrice = state.carrito.reduce((acc, item) => acc + (item.cantidad * item.precio), 0);
    
    if (totalItems > 0) {
        bar.style.display = 'flex';
        countEl.textContent = totalItems;
        totalEl.textContent = `$${totalPrice.toFixed(2)}`;
    } else {
        bar.style.display = 'none';
    }
}

function openCart() {
    const modal = document.getElementById('cart-modal');
    modal.style.display = 'flex';
    renderCartItems();
    
    const input = document.getElementById('cliente-nombre-input');
    if (input) {
        input.value = sessionStorage.getItem('cliente_nombre') || '';
    }
}

function closeCart() {
    document.getElementById('cart-modal').style.display = 'none';
}

function renderCartItems() {
    const list = document.getElementById('cart-items-list');
    list.innerHTML = '';
    
    if (state.carrito.length === 0) {
        list.innerHTML = '<p style="text-align:center; padding: 20px;">Tu carrito está vacío.</p>';
        closeCart();
        return;
    }
    
    state.carrito.forEach((item, index) => {
        const el = document.createElement('div');
        el.className = 'cart-item';
        el.innerHTML = `
            <div class="item-main-row">
                <div class="item-qty-control">
                    <button class="qty-btn" onclick="updateQty(${index}, -1)">−</button>
                    <span style="font-weight: 800; min-width: 20px; text-align:center;">${item.cantidad}</span>
                    <button class="qty-btn" onclick="updateQty(${index}, 1)">+</button>
                </div>
                <div class="item-details">
                    <div class="item-name">${item.producto}</div>
                    <div class="item-price">$${item.precio.toFixed(2)}/u</div>
                </div>
                <div class="item-subtotal">$${(item.cantidad * item.precio).toFixed(2)}</div>
            </div>
            <input type="text" class="item-notes" placeholder="¿Nota especial? (ej: Sin cebolla)" 
                   value="${item.notas}" onchange="updateNotes(${index}, this.value)">
        `;
        list.appendChild(el);
    });
    
    const total = state.carrito.reduce((acc, item) => acc + (item.cantidad * item.precio), 0);
    document.getElementById('modal-total').textContent = `$${total.toFixed(2)}`;
}

function updateQty(index, delta) {
    state.carrito[index].cantidad += delta;
    if (state.carrito[index].cantidad <= 0) {
        state.carrito.splice(index, 1);
    }
    renderCartItems();
    actualizarBarraCarrito();
}

function updateNotes(index, value) {
    state.carrito[index].notas = value;
}

// 4. ENVÍO DEL PEDIDO
async function enviarPedidoCliente() {
    if (state.carrito.length === 0 || state.isSending) return;
    
    // Sync if name was typed in cart input
    const nameInput = document.getElementById('cliente-nombre-input');
    const clienteNombre = nameInput ? nameInput.value.trim() : '';
    if (clienteNombre) {
        state.alias = clienteNombre;
        state.mesa = clienteNombre;
        sessionStorage.setItem('cliente_alias', clienteNombre);
        sessionStorage.setItem('cliente_nombre', clienteNombre);
        actualizarUIIdentidad();
    }

    if (!state.alias) {
        Toast.error("Identificación requerida 🗣️", "Por favor ingresa tu nombre o alias.");
        openMesaModal();
        return;
    }
    
    const confirmacion = confirm("¿Deseas enviar tu pedido a la cocina?");
    if (!confirmacion) return;

    state.isSending = true;
    const btn = document.getElementById('send-order-btn');
    btn.disabled = true;
    btn.innerHTML = '<div class="loading-spinner" style="width:20px; height:20px; margin:0;"></div> Enviando...';

    const total = state.carrito.reduce((acc, item) => acc + (item.cantidad * item.precio), 0);
    
    const pedidoData = {
        mesa: state.alias,
        mesero: "Autoservicio QR",
        items: state.carrito,
        total: total
    };

    console.log("🚀 Iniciando proceso de envío de pedido CLIENTE...");
    console.log("📦 Datos brutos del pedido:", pedidoData);

    try {
        const response = await DataService.crearPedido(pedidoData);
        if (response && response.id) {
            state.activeOrderId = response.id;
            sessionStorage.setItem('active_order_id', response.id);
            startTrackingOrder(response.id);
            showSuccess();
        } else {
            throw new Error("Respuesta del servidor incompleta");
        }
    } catch (e) {
        if (state.supabaseSchemaError) {
            console.warn("⚠️ Detectado error de tabla en Supabase. Simulando pedido localmente como respaldo...");
            const tempId = 'sim-' + Date.now();
            state.activeOrderId = tempId;
            sessionStorage.setItem('active_order_id', tempId);
            showSuccess(true); // Pasar true para indicar MODO DEMO
            
            // Simular cambios de estado locales para que el usuario vea cómo funcionaría
            setTimeout(() => updateStatusUI('cocinando'), 5000);
            setTimeout(() => updateStatusUI('listo'), 15000);
            return;
        }

        Toast.error("Error", e.message);
        state.isSending = false;
        btn.disabled = false;
        btn.innerHTML = 'Enviar pedido a cocina';
    }
}

// 5. NUEVA LOGICA DE SEGUIMIENTO Y SERVICIOS
// 5. NUEVA LOGICA DE SEGUIMIENTO Y SERVICIOS
let pmQRCodeObj = null;

async function generarPagoMovilQR() {
    try {
        const pm = await DataService.getSettings('pago_movil');
        const qrContainer = document.getElementById('pm-qr-container');
        const qrBox = document.getElementById('pm-qr-box');
        const qrTotalVesEl = document.getElementById('pm-qr-total-ves');
        
        if (pm && qrContainer && qrBox && qrTotalVesEl) {
            // Calcular total en bolívares
            const totalUsd = state.carrito.reduce((acc, item) => acc + (item.cantidad * item.precio), 0);
            const totalVes = totalUsd * state.tasaCambio;
            
            qrTotalVesEl.textContent = totalVes.toFixed(2);
            qrBox.innerHTML = ''; // Limpiar previo
            
            // Extraer el código del banco (por ejemplo, "0102" si es "0102 - Banco de Venezuela")
            const bancoCode = (pm.banco || '').match(/\d{4}/)?.[0] || '0102';
            
            // Payload estándar de Pago Móvil en Venezuela:
            // pagomovil:{codigo_banco}:{telefono}:{rif}:{monto_formateado}
            const payload = `pagomovil:${bancoCode}:${pm.telefono}:${pm.rif}:${totalVes.toFixed(2)}`;
            
            pmQRCodeObj = new QRCode(qrBox, {
                text: payload,
                width: 140,
                height: 140,
                colorDark : "#000000",
                colorLight : "#ffffff",
                correctLevel : QRCode.CorrectLevel.M
            });
            
            qrContainer.style.display = 'flex';
        }
    } catch (e) {
        console.error("No se pudo generar el QR de Pago Móvil:", e);
    }
}

function openPagoMovil() {
    if (state.carrito.length === 0) {
        Toast.info("Carrito vacío", "Agrega algunos platillos antes de pagar.");
        openCart();
        return;
    }
    document.getElementById('pm-modal').style.display = 'flex';
    generarPagoMovilQR();
}

function closePagoMovil() {
    document.getElementById('pm-modal').style.display = 'none';
}

async function confirmarPagoMovil() {
    // Sync if name was typed in cart input
    const nameInput = document.getElementById('cliente-nombre-input');
    const clienteNombre = nameInput ? nameInput.value.trim() : '';
    if (clienteNombre) {
        state.alias = clienteNombre;
        state.mesa = clienteNombre;
        sessionStorage.setItem('cliente_alias', clienteNombre);
        sessionStorage.setItem('cliente_nombre', clienteNombre);
        actualizarUIIdentidad();
    }

    if (!state.alias) {
        Toast.error("Identificación requerida 🗣️", "Por favor ingresa tu nombre o alias.");
        closePagoMovil();
        openMesaModal();
        return;
    }

    const banco = document.getElementById('pm-banco').value;
    const telefono = document.getElementById('pm-telefono').value.trim();
    const referencia = document.getElementById('pm-referencia').value.trim();

    // Validaciones estrictas anti-error de dedo
    if (!banco) return Toast.error("Faltan datos", "Selecciona el banco emisor.");
    if (telefono.length < 10) return Toast.error("Teléfono inválido", "Ingresa un número de teléfono válido.");
    if (referencia.length < 4) return Toast.error("Referencia incompleta", "Ingresa al menos los últimos 4 dígitos.");

    if (!confirm("¿Confirmar envío de datos de pago?")) return;

    state.isSending = true;
    const total = state.carrito.reduce((acc, item) => acc + (item.cantidad * item.precio), 0);
    
    // Preparar payload con metadatos de pago
    const pedidoData = {
        mesa: state.alias,
        mesero: "Cliente QR (Pago Móvil)",
        items: state.carrito,
        total: total,
        metodo_pago: 'pago_movil',
        pago_referencia: referencia,
        pago_telefono: telefono,
        pago_banco: banco,
        estado_pago: 'pendiente'
    };

    try {
        const response = await DataService.crearPedido(pedidoData);
        if (response && response.id) {
            state.activeOrderId = response.id;
            sessionStorage.setItem('active_order_id', response.id);
            
            // Mostrar pantalla de espera y cerrar modal
            closePagoMovil();
            closeCart();
            document.getElementById('pm-wait-screen').style.display = 'flex';
            
            // Iniciar temporizador de cuenta regresiva
            startPMCountdown();
            
            // Escuchar aprobación en tiempo real
            startTrackingPagoMovil(response.id);
        }
    } catch (e) {
        Toast.error("Error al procesar", e.message);
        state.isSending = false;
    }
}

let pmTimerInterval = null;

function startPMCountdown() {
    if (pmTimerInterval) clearInterval(pmTimerInterval);
    let secondsLeft = 180; // 3 minutos
    const displayEl = document.getElementById('pm-timer-display');
    const fallbackEl = document.getElementById('pm-timer-fallback');
    
    if (displayEl) displayEl.textContent = "03:00";
    if (fallbackEl) fallbackEl.style.display = 'none';
    
    pmTimerInterval = setInterval(() => {
        secondsLeft--;
        if (secondsLeft <= 0) {
            clearInterval(pmTimerInterval);
            if (displayEl) displayEl.textContent = "00:00";
            if (fallbackEl) fallbackEl.style.display = 'block';
            // Notificar a caja automáticamente tras timeout
            try {
                DataService.crearSolicitud(state.alias || "Cliente QR", "asistencia");
            } catch(e) { console.warn(e); }
        } else {
            const mins = Math.floor(secondsLeft / 60);
            const secs = secondsLeft % 60;
            if (displayEl) {
                displayEl.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
            }
        }
    }, 1000);
}

function stopPMCountdown() {
    if (pmTimerInterval) {
        clearInterval(pmTimerInterval);
        pmTimerInterval = null;
    }
}

function startTrackingPagoMovil(orderId) {
    DataService.suscribirAPedidos((payload) => {
        if (payload.new && payload.new.id == orderId) {
            // Si el estado_pago cambia
            if (payload.new.estado_pago === 'aprobado') {
                stopPMCountdown();
                document.getElementById('pm-wait-message').innerHTML = 
                    "<span style='color:var(--success); font-weight:800;'>¡PAGO APROBADO!</span><br>Ya puedes retirar tu factura en caja.";
                setTimeout(() => {
                    document.getElementById('pm-wait-screen').style.display = 'none';
                    showSuccess();
                }, 3000);
            } else if (payload.new.estado_pago === 'rechazado') {
                stopPMCountdown();
                document.getElementById('pm-wait-screen').style.display = 'none';
                Toast.error("Pago Rechazado", "La caja no pudo validar tu referencia. Por favor intentalo de nuevo.");
                openPagoMovil();
            }
        }
    });
}

window.openPagoMovil = openPagoMovil;
window.closePagoMovil = closePagoMovil;
window.confirmarPagoMovil = confirmarPagoMovil;

function startTrackingOrder(orderId) {
    console.log("📍 Siguiendo pedido:", orderId);
    DataService.suscribirAPedidos((payload) => {
        if (payload.new && payload.new.id == orderId) {
            updateStatusUI(payload.new.estado);
        }
    });

    // Mostrar barra de estado en el DOM si el main content ya tiene el éxito
    setTimeout(() => updateStatusUI('pendiente'), 500); 
}

function updateStatusUI(estado) {
    const textEl = document.getElementById('status-step-text');
    const progressEl = document.getElementById('status-progress-bar');
    
    if (!textEl) return;

    let text = "Recibido 📝";
    let width = "33%";

    if (estado === 'cocinando') {
        text = "En Preparación 🍳";
        width = "66%";
    } else if (estado === 'listo') {
        text = "¡Listo para Servir! 🍽️";
        width = "100%";
    } else if (estado === 'entregado' || estado === 'pagado') {
        text = "Entregado ✅";
        width = "100%";
    }

    textEl.textContent = `ESTADO: ${text}`;
    if (progressEl) progressEl.style.width = width;
}

async function solicitarMesa(tipo) {
    if (!state.alias) {
        Toast.error("Identificación requerida 🗣️", "Por favor ingresa tu nombre o alias haciendo clic en el indicador superior.");
        openMesaModal();
        return;
    }
    try {
        await DataService.crearSolicitud(state.alias, tipo);
        if (tipo === 'cuenta') {
            Toast.success("Cuenta Solicitada", "El cajero te atenderá en breve.");
        } else {
            Toast.success("Mesero Llamado", "Un mesero se dirigirá a tu lugar.");
        }
    } catch (e) {
        console.error("Error al solicitar mesa:", e);
        if (e.message && e.message.includes("row-level security")) {
            Toast.error("Error de Permisos", "Tu base de datos de Supabase no permite enviar alertas. Asegúrate de ejecutar el archivo database/esquema.sql en el SQL Editor de Supabase.");
        } else {
            Toast.error("Error", e.message || "No se pudo enviar la solicitud.");
        }
    }
}

window.solicitarMesa = solicitarMesa;

function showSuccess(isDemo = false) {
    closeCart();
    document.getElementById('cart-bar').style.display = 'none';
    const container = document.getElementById('main-content');
    container.innerHTML = `
        <div class="success-view" style="animation: pageFadeIn 0.8s ease-out forwards;">
            <div style="background: ${isDemo ? 'rgba(251, 191, 36, 0.1)' : 'rgba(0, 230, 118, 0.1)'}; width: 100px; height: 100px; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 30px;">
                <ion-icon name="${isDemo ? 'flask-outline' : 'checkmark-circle'}" style="font-size: 4rem; color: ${isDemo ? '#facc15' : 'var(--success)'};"></ion-icon>
            </div>
            
            <h2 style="margin-bottom: 12px; font-size: 1.8rem; font-weight: 800;">${isDemo ? '¡Simulación Enviada!' : '¡Pedido enviado con éxito!'}</h2>
            <p style="color: var(--text-muted); ${isDemo ? 'font-weight:bold;' : ''} margin-bottom: 40px; font-size: 1.1rem; line-height: 1.6;">
                ${isDemo ? '⚠️ ATENCIÓN: El pedido NO se envió a cocina real porque faltan tablas en Supabase. Estás viendo una simulación local.' : 'Estamos preparando tus platillos deliciosos.<br>En un momento te los llevaremos a tu mesa.'}
            </p>
            
            <div style="background: var(--card-bg); padding: 30px; border-radius: 24px; border: 1px solid var(--border); box-shadow: var(--shadow); text-align: left; position: relative; overflow: hidden;">
                <!-- Decoración -->
                <div style="position: absolute; top: -20px; right: -20px; width: 60px; height: 60px; background: var(--primary); opacity: 0.05; border-radius: 50%;"></div>
                
                <p id="status-step-text" style="font-weight: 800; color: var(--primary); text-transform: uppercase; letter-spacing: 1px; font-size: 0.9rem; display: flex; align-items: center; gap: 8px;">
                    <ion-icon name="restaurant"></ion-icon>
                    ESTADO: ${isDemo ? 'SIMULANDO PREPARACIÓN 🧪' : 'EN PREPARACIÓN 🥑'}
                </p>
                <div style="width: 100%; background: rgba(255,255,255,0.05); height: 12px; border-radius: 100px; margin-top: 15px; overflow: hidden; border: 1px solid rgba(255,255,255,0.05);">
                    <div id="status-progress-bar" style="width: 33%; background: linear-gradient(90deg, var(--primary), #ffa726); height: 100%; transition: width 1s cubic-bezier(0.4, 0, 0.2, 1);"></div>
                </div>
                <p style="font-size: 0.9rem; margin-top: 18px; color: var(--text-muted); line-height: 1.5;">
                    ${isDemo ? '<strong>Para corregir esto:</strong> Copia el contenido de <b>database/esquema.sql</b> y ejecútalo en el SQL Editor de tu Supabase.' : 'Si necesitas algo más, puedes volver a llamar al mesero pulsando el botón de la campana arriba.'}
                </p>
            </div>
            
            <div style="display: flex; flex-direction: column; gap: 12px; margin-top: 30px;">
                <button class="confirm-btn" style="background: #ff6b00 !important; color: #ffffff !important; margin: 0; box-shadow: 0 10px 20px rgba(255, 107, 0, 0.3);" onclick="location.reload()">
                    <ion-icon name="restaurant-outline"></ion-icon>
                    ${isDemo ? 'Volver al Menú' : 'Hacer otro pedido'}
                </button>
                <button class="confirm-btn" style="background: rgba(255,255,255,0.08) !important; color: #ffffff !important; border: 1px solid rgba(255,255,255,0.1) !important; margin: 0; font-size: 1rem; box-shadow: none;" onclick="solicitarMesa('mesero')">
                    <ion-icon name="notifications-outline"></ion-icon>
                    Llamar al mesero
                </button>
            </div>
        </div>
    `;
}
