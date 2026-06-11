// SUPABASE CLIENT INITIALIZATION & ENLACE DE DATOS EN TIEMPO REAL
// Soporta sincronización real con Supabase o simulación robusta local mediante BroadcastChannel si aún no está configurado.

const SUPABASE_CONFIG_KEY = 'restaurante_supabase_config';

// 1. OBTENER CONFIGURACIÓN (De LocalStorage o de variables por defecto)
function getSupabaseConfig() {
    const saved = localStorage.getItem(SUPABASE_CONFIG_KEY);
    if (saved) {
        try {
            return JSON.parse(saved);
        } catch (e) {
            console.error("Error leyendo configuración guardada:", e);
        }
    }
    // Valores plantilla modificables por el desarrollador
    return {
        url: "", // ej: "https://vzywyvzvz.supabase.co"
        anonKey: "" // ej: "eyJhbGciOi..."
    };
}

// 2. COMPROBAR VALIDEZ DE CONFIGURACIÓN
function isSupabaseConfigured(config) {
    return config && config.url && config.url.trim() !== "" && config.anonKey && config.anonKey.trim() !== "";
}

const currentConfig = getSupabaseConfig();
let supabaseClient = null;
let isRealSupabase = false;

if (isSupabaseConfigured(currentConfig)) {
    try {
        // Inicializar cliente oficial de Supabase
        supabaseClient = supabase.createClient(currentConfig.url, currentConfig.anonKey);
        isRealSupabase = true;
        console.log("✅ Conectado exitosamente al cliente de Supabase Nube.");
    } catch (err) {
        console.error("❌ Error inicializando Supabase con la configuración dada:", err);
    }
} else {
    console.log("ℹ️ Supabase no configurado. Iniciando canal de simulación local en tiempo real.");
}

// 3. CANAL DE BROADCAST LOCAL PARA SINCRONIZACIÓN MULTI-PESTAÑA (Offline / Demo)
const localSyncChannel = new BroadcastChannel('restaurante_sincronizacion_local');

// Estructuración de datos simulados en LocalStorage si no hay conexión real
function getLocalPedidos() {
    const data = localStorage.getItem('demo_pedidos');
    return data ? JSON.parse(data) : [];
}

function saveLocalPedidos(pedidos) {
    localStorage.setItem('demo_pedidos', JSON.stringify(pedidos));
    // Emitir a otras pestañas
    localSyncChannel.postMessage({ type: 'PEDIDOS_CHANGED', data: pedidos });
}

function getLocalAuditoria() {
    const data = localStorage.getItem('demo_auditoria');
    return data ? JSON.parse(data) : [];
}

function saveLocalAuditoria(auditoria) {
    localStorage.setItem('demo_auditoria', JSON.stringify(auditoria));
    localSyncChannel.postMessage({ type: 'AUDITORIA_CHANGED', data: auditoria });
}

// Inicializar base de datos local de prueba si está vacía
if (!localStorage.getItem('demo_pedidos')) {
    const pedidosIniciales = [
        {
            id: 1,
            mesa: "Mesa 3",
            mesero: "Carlos Gómez",
            items: [
                { producto: "Hamburguesa Premium", cantidad: 2, precio: 12.50, notas: "Una sin cebolla" },
                { producto: "Papas Fritas", cantidad: 1, precio: 4.00, notas: "" },
                { producto: "Refresco Sabor Cola", cantidad: 2, precio: 2.50, notas: "Bien fríos" }
            ],
            total: 31.50,
            estado: "pendiente",
            creado_en: new Date(Date.now() - 20 * 60 * 1000).toISOString(),
            actualizado_en: new Date(Date.now() - 20 * 60 * 1000).toISOString()
        },
        {
            id: 2,
            mesa: "Mesa 7",
            mesero: "María Rojas",
            items: [
                { producto: "Pizza Personal Pepperoni", cantidad: 1, precio: 15.00, notas: "Borde de queso" },
                { producto: "Té Frío Limón", cantidad: 1, precio: 3.00, notas: "" }
            ],
            total: 18.00,
            estado: "cocinando",
            creado_en: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
            actualizado_en: new Date(Date.now() - 8 * 60 * 1000).toISOString()
        },
        {
            id: 3,
            mesa: "Mesa 1",
            mesero: "Carlos Gómez",
            items: [
                { producto: "Tacos de Res (x3)", cantidad: 2, precio: 8.50, notas: "Limón extra" },
                { producto: "Agua Mineral", cantidad: 2, precio: 2.00, notas: "" }
            ],
            total: 21.00,
            estado: "listo",
            creado_en: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
            actualizado_en: new Date(Date.now() - 5 * 60 * 1000).toISOString()
        }
    ];
    saveLocalPedidos(pedidosIniciales);
}

if (!localStorage.getItem('demo_auditoria')) {
    const auditoriaInicial = [
        {
            id: 101,
            pedido_id: 99,
            monto: 45.50,
            metodo_pago: "tarjeta",
            mesero: "María Rojas",
            creado_en: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()
        },
        {
            id: 102,
            pedido_id: 98,
            monto: 15.00,
            metodo_pago: "efectivo",
            mesero: "Carlos Gómez",
            creado_en: new Date(Date.now() - 1.5 * 60 * 60 * 1000).toISOString()
        }
    ];
    saveLocalAuditoria(auditoriaInicial);
}

// 4. API UNIFICADA DE ACCESO A DATOS (Abstrae Supabase / LocalSim)
const DataService = {
    isReal: () => isRealSupabase,
    getConfig: () => currentConfig,
    saveConfig: (url, anonKey) => {
        localStorage.setItem(SUPABASE_CONFIG_KEY, JSON.stringify({ url, anonKey }));
        location.reload();
    },
    resetConfig: () => {
        localStorage.removeItem(SUPABASE_CONFIG_KEY);
        location.reload();
    },

    // Obtener lista completa de pedidos activos
    async fetchMenu() {
        if (isRealSupabase) {
            try {
                const { data, error } = await supabaseClient
                    .from('menu')
                    .select('*');
                if (error) throw error;
                return data;
            } catch (err) {
                console.error("Error cargando menú desde Supabase:", err);
                return null;
            }
        } else {
            return null; // O devolver Mock si se prefiere
        }
    },

    // Obtener lista completa de pedidos activos
    async fetchPedidos() {
        if (isRealSupabase) {
            try {
                const { data, error } = await supabaseClient
                    .from('pedidos')
                    .select('*')
                    .neq('estado', 'pagado')
                    .order('creado_en', { ascending: true });
                if (error) throw error;
                return data;
            } catch (err) {
                console.error("Error cargando pedidos desde Supabase, reintentando local:", err);
                return getLocalPedidos().filter(p => p.estado !== 'pagado');
            }
        } else {
            return getLocalPedidos().filter(p => p.estado !== 'pagado');
        }
    },

    // Crear un nuevo pedido
    async crearPedido(pedidoCustom) {
        if (isRealSupabase) {
            try {
                const { data, error } = await supabaseClient
                    .from('pedidos')
                    .insert([{
                        mesa: pedidoCustom.mesa,
                        mesero: pedidoCustom.mesero,
                        items: pedidoCustom.items,
                        total: pedidoCustom.total,
                        estado: 'pendiente'
                    }])
                    .select();
                if (error) throw error;
                return data[0];
            } catch (err) {
                console.error("Error insertando en Supabase, guardando local:", err);
                return insertLocalPedido(pedidoCustom);
            }
        } else {
            return insertLocalPedido(pedidoCustom);
        }
    },

    // Actualizar el estado de un pedido específico
    async actualizarEstadoPedido(id, nuevoEstado) {
        if (isRealSupabase) {
            try {
                const { data, error } = await supabaseClient
                    .from('pedidos')
                    .update({ estado: nuevoEstado })
                    .eq('id', id)
                    .select();
                if (error) throw error;
                return data[0];
            } catch (err) {
                console.error("Error actualizando Supabase, aplicando local:", err);
                return updateLocalPedidoEstado(id, nuevoEstado);
            }
        } else {
            return updateLocalPedidoEstado(id, nuevoEstado);
        }
    },

    // Registrar cobro e incluir registro financiero de auditoria
    async cobrarsePedido(idPedido, monto, metodoPago, mesero) {
        if (isRealSupabase) {
            try {
                // 1. Guardar log financiero
                const { error: errAud } = await supabaseClient
                    .from('auditoria_financiera')
                    .insert([{
                        pedido_id: idPedido,
                        monto: monto,
                        metodo_pago: metodoPago,
                        mesero: mesero
                    }]);
                if (errAud) throw errAud;

                // 2. Marcar pedido como 'pagado'
                const { data, error: errPed } = await supabaseClient
                    .from('pedidos')
                    .update({ estado: 'pagado' })
                    .eq('id', idPedido)
                    .select();
                if (errPed) throw errPed;

                return data[0];
            } catch (err) {
                console.error("Error realizando el pago en Supabase, procesando local:", err);
                return pagarLocalPedido(idPedido, monto, metodoPago, mesero);
            }
        } else {
            return pagarLocalPedido(idPedido, monto, metodoPago, mesero);
        }
    },

    // Obtener logs de auditoría financiera unidos con mesa y estado del pedido correspondientes
    async fetchAuditoria() {
        if (isRealSupabase) {
            try {
                // Obtenemos los logs financieros
                const { data: logs, error: errAud } = await supabaseClient
                    .from('auditoria_financiera')
                    .select('*')
                    .order('creado_en', { ascending: false });
                if (errAud) throw errAud;

                // Obtenemos todos los pedidos para cruzar mesa y productos
                const { data: pedidos, error: errPed } = await supabaseClient
                    .from('pedidos')
                    .select('id, mesa, items, estado');
                
                const merged = (logs || []).map(log => {
                    const matchedPed = (pedidos || []).find(p => p.id == log.pedido_id);
                    return {
                        ...log,
                        pedidos: matchedPed ? { mesa: matchedPed.mesa, items: matchedPed.items, estado: matchedPed.estado } : null
                    };
                });
                return merged;
            } catch (err) {
                console.error("Error auditando Supabase, cruzando dinámicamente en RAM local:", err);
                return this.getLocalAuditoriaWithPedidos();
            }
        } else {
            return this.getLocalAuditoriaWithPedidos();
        }
    },

    getLocalAuditoriaWithPedidos() {
        const logs = getLocalAuditoria();
        const pedidos = getLocalPedidos();
        return logs.map(l => {
            const matchedPed = pedidos.find(p => p.id == l.pedido_id);
            return {
                ...l,
                pedidos: matchedPed ? { mesa: matchedPed.mesa, items: matchedPed.items, estado: matchedPed.estado } : null
            };
        });
    },

    // Suscribirse de forma reactiva a cambios en pedidos
    suscribirAPedidos(onUpdateCallback) {
        if (isRealSupabase) {
            const channel = supabaseClient
                .channel('pedidos-realtime')
                .on('postgres_changes', { event: '*', schema: 'public', table: 'pedidos' }, (payload) => {
                    console.log('Cambio recibido de Supabase en tiempo real:', payload);
                    // Disparar recarga a través del callback
                    onUpdateCallback(payload);
                })
                .subscribe();
            return () => {
                supabaseClient.removeChannel(channel);
            };
        } else {
            // Escuchar canal Broadcast para simular web sockets en tiempo real
            const handler = (event) => {
                if (event.data && event.data.type === 'PEDIDOS_CHANGED') {
                    onUpdateCallback({ eventType: 'SYNC', new: event.data.data });
                }
            };
            localSyncChannel.addEventListener('message', handler);
            return () => {
                localSyncChannel.removeEventListener('message', handler);
            };
        }
    },

    // Suscribirse reactivamente a auditoria
    suscribirAAuditoria(onUpdateCallback) {
        if (isRealSupabase) {
            const channel = supabaseClient
                .channel('auditoria-realtime')
                .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'auditoria_financiera' }, (payload) => {
                    console.log('Fondo recibido en Auditoría:', payload);
                    onUpdateCallback(payload);
                })
                .subscribe();
            return () => {
                supabaseClient.removeChannel(channel);
            };
        } else {
            const handler = (event) => {
                if (event.data && event.data.type === 'AUDITORIA_CHANGED') {
                    onUpdateCallback({ eventType: 'INSERT', new: event.data.data });
                }
            };
            localSyncChannel.addEventListener('message', handler);
            return () => {
                localSyncChannel.removeEventListener('message', handler);
            };
        }
    }
};

// --- AYUDANTES DE LOGICA LOCAL (MOCK ENGINE) ---

function insertLocalPedido(pedidoCustom) {
    const pedidos = getLocalPedidos();
    const nuevoId = pedidos.length > 0 ? Math.max(...pedidos.map(p => p.id)) + 1 : 1;
    const nuevoPedido = {
        id: nuevoId,
        mesa: pedidoCustom.mesa,
        mesero: pedidoCustom.mesero,
        items: pedidoCustom.items,
        total: pedidoCustom.total,
        estado: 'pendiente',
        creado_en: new Date().toISOString(),
        actualizado_en: new Date().toISOString()
    };
    pedidos.push(nuevoPedido);
    saveLocalPedidos(pedidos);
    return nuevoPedido;
}

function updateLocalPedidoEstado(id, nuevoEstado) {
    const pedidos = getLocalPedidos();
    const idx = pedidos.findIndex(p => p.id == id);
    if (idx !== -1) {
        pedidos[idx].estado = nuevoEstado;
        pedidos[idx].actualizado_en = new Date().toISOString();
        saveLocalPedidos(pedidos);
        return pedidos[idx];
    }
    return null;
}

function pagarLocalPedido(idPedido, monto, metodoPago, mesero) {
    // 1. Marcar pedido local
    const pedidos = getLocalPedidos();
    const idx = pedidos.findIndex(p => p.id == idPedido);
    let meseroNom = mesero || "Desconocido";
    if (idx !== -1) {
        pedidos[idx].estado = 'pagado';
        pedidos[idx].actualizado_en = new Date().toISOString();
        meseroNom = pedidos[idx].mesero;
        saveLocalPedidos(pedidos);
    }

    // 2. Grabar logs auditoria
    const logs = getLocalAuditoria();
    const nuevoId = logs.length > 0 ? Math.max(...logs.map(l => l.id)) + 1 : 101;
    const nuevoLog = {
        id: nuevoId,
        pedido_id: parseInt(idPedido),
        monto: parseFloat(monto),
        metodo_pago: metodoPago,
        mesero: meseroNom,
        creado_en: new Date().toISOString()
    };
    logs.unshift(nuevoLog);
    saveLocalAuditoria(logs);
    return nuevoLog;
}
