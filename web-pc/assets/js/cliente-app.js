/**
 * LOGICA DEL CLIENTE - AUTOGESTIÓN POR QR
 * Permite al comensal ver el menú, armar su carrito y enviar el pedido.
 */

const MENU_DEFAULT_CLIENTE = [
    { nombre: "Hamburguesa Premium", precio: 12.50, categoria: "COMIDA", descripcion: "Queso cheddar, tocino, aderezo gourmet.", emoji: "🍔" },
    { nombre: "Pizza Personal Pepperoni", precio: 15.00, categoria: "COMIDA", descripcion: "Salsa de la casa, pepperoni, mozzarella.", emoji: "🍕" },
    { nombre: "Tacos de Res (x3)", precio: 8.50, categoria: "COMIDA", descripcion: "Cebollitas asadas, cilantro, salsas.", emoji: "🌮" },
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
    isSending: false
};

// 1. INICIALIZACIÓN
document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

async function initApp() {
    // Capturar mesa de la URL
    const urlParams = new URLSearchParams(window.location.search);
    const mesaParam = urlParams.get('mesa');
    
    if (mesaParam) {
        state.mesa = mesaParam;
        document.getElementById('mesa-indicator').textContent = `Mesa ${mesaParam}`;
    } else {
        document.getElementById('mesa-indicator').textContent = "Mesa no detectada";
        showQRError();
        return;
    }

    // Cargar Menú
    await cargarMenu();

    // Sincronizar conexión Supabase UI
    const conn = await DataService.checkConnection();
    console.log("Supabase Connection Status:", conn.message);
    if (!conn.success && DataService.isReal()) {
        console.warn("⚠️ Advertencia: El cliente tiene configurado Supabase pero no hay respuesta del servidor.");
        alert("Atención: No se pudo conectar con el servidor en la nube. Los pedidos podrían no llegar a la cocina. Verifica tu conexión a internet o la configuración del sistema.");
    }

    // Suscribirse a cambios en tiempo real del menú
    DataService.suscribirAMenu(() => {
        console.log("Sincronizando menú por actualización en tiempo real...");
        cargarMenu();
    });
}

function showQRError() {
    const content = document.getElementById('main-content');
    content.innerHTML = `
        <div class="qr-alert">
            <ion-icon name="qr-code-outline" style="font-size: 3rem; margin-bottom: 12px;"></ion-icon>
            <p><strong>¡Vaya! No hemos detectado tu mesa.</strong></p>
            <p>Por favor, escanea nuevamente el código QR que se encuentra en tu mesa para poder hacer tu pedido.</p>
        </div>
    `;
    document.getElementById('cart-bar').style.display = 'none';
}

async function cargarMenu() {
    try {
        let menuData = await DataService.fetchMenu();
        
        if (!menuData || menuData.length === 0) {
            console.log("Usando menú por defecto (Supabase no configurado o tabla vacía)");
            menuData = MENU_DEFAULT_CLIENTE;
        }
        
        state.menu = menuData;
        renderMenu();
    } catch (e) {
        console.error("Error cargando menú:", e);
        state.menu = MENU_DEFAULT_CLIENTE;
        renderMenu();
    }
}

// 2. RENDERIZACIÓN
function renderMenu() {
    const container = document.getElementById('main-content');
    container.innerHTML = '';
    
    if (state.menu.length === 0) {
        container.innerHTML = '<p style="text-align:center;">No hay productos disponibles por ahora.</p>';
        return;
    }

    // Agrupar por Categoría
    const categorias = [...new Set(state.menu.map(p => p.categoria))];
    
    categorias.forEach(cat => {
        const section = document.createElement('section');
        section.className = 'categoria-section';
        
        const title = document.createElement('h2');
        title.className = 'categoria-title';
        title.innerHTML = `<span>${getCategoryEmoji(cat)}</span> ${cat}`;
        section.appendChild(title);
        
        const grid = document.createElement('div');
        grid.className = 'menu-grid';
        
        const products = state.menu.filter(p => p.categoria === cat);
        products.forEach(prod => {
            const card = document.createElement('div');
            card.className = 'platillo-card';
            card.innerHTML = `
                <div class="platillo-emoji">${prod.emoji || '🍽️'}</div>
                <div class="platillo-info">
                    <div class="platillo-name">${prod.nombre}</div>
                    <div class="platillo-desc">${prod.descripcion || ''}</div>
                    <div class="platillo-footer">
                        <div class="platillo-price">$${parseFloat(prod.precio).toFixed(2)}</div>
                        <button class="add-btn" onclick="addToCart('${prod.nombre}', ${prod.precio})">
                            <ion-icon name="add"></ion-icon>
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
    // Feedback visual breve
    const addSound = new Audio('https://www.soundjay.com/buttons/button-37.mp3');
    // addSound.play(); // Opcional
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
            <div class="item-qty-control">
                <button class="qty-btn" onclick="updateQty(${index}, -1)">-</button>
                <span style="font-weight: 700;">${item.cantidad}</span>
                <button class="qty-btn" onclick="updateQty(${index}, 1)">+</button>
            </div>
            <div class="item-details">
                <div class="item-name">${item.producto}</div>
                <div class="item-price">$${item.precio.toFixed(2)}/u</div>
                <input type="text" class="item-notes" placeholder="¿Alguna nota especial?" 
                       value="${item.notas}" onchange="updateNotes(${index}, this.value)">
            </div>
            <div class="item-subtotal">$${(item.cantidad * item.precio).toFixed(2)}</div>
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
async function confirmOrder() {
    if (state.carrito.length === 0 || state.isSending) return;
    
    const confirmacion = confirm("¿Deseas enviar tu pedido a la cocina?");
    if (!confirmacion) return;

    state.isSending = true;
    const btn = document.getElementById('send-order-btn');
    btn.disabled = true;
    btn.innerHTML = '<div class="loading-spinner" style="width:20px; height:20px; margin:0;"></div> Enviando...';

    const total = state.carrito.reduce((acc, item) => acc + (item.cantidad * item.precio), 0);
    
    const pedidoData = {
        mesa: `Mesa ${state.mesa}`,
        mesero: "Autoservicio QR",
        items: state.carrito,
        total: total
    };

    console.log("Intentando enviar pedido:", pedidoData);

    try {
        const response = await DataService.crearPedido(pedidoData);
        console.log("Respuesta de DataService:", response);
        if (response) {
            showSuccess();
        } else {
            throw new Error("No se recibió respuesta del servidor");
        }
    } catch (e) {
        console.error("Error enviando pedido:", e);
        alert("Hubo un error al enviar tu pedido. Por favor, intenta de nuevo o llama a un mesero.");
        state.isSending = false;
        btn.disabled = false;
        btn.textContent = "Enviar pedido a cocina";
    }
}

function showSuccess() {
    closeCart();
    document.getElementById('cart-bar').style.display = 'none';
    const container = document.getElementById('main-content');
    container.innerHTML = `
        <div class="success-view">
            <ion-icon name="checkmark-circle" style="font-size: 5rem; color: var(--success); margin-bottom: 20px;"></ion-icon>
            <h2 style="margin-bottom: 12px;">¡Pedido enviado con éxito!</h2>
            <p style="color: var(--text-muted); margin-bottom: 30px;">Estamos preparando tus platillos deliciosos. En un momento te los llevaremos a tu mesa.</p>
            <div style="background: white; padding: 20px; border-radius: 16px; border: 1px solid var(--border); box-shadow: var(--shadow);">
                <p style="font-weight: 700; color: var(--primary);">ESTADO: EN PREPARACIÓN 🥑</p>
                <p style="font-size: 0.85rem; margin-top: 8px;">Si necesitas algo más, puedes volver a escanear el código QR.</p>
            </div>
            <button class="confirm-btn" style="margin-top: 40px; background: var(--text-main);" onclick="location.reload()">
                Hacer otro pedido
            </button>
        </div>
    `;
}
