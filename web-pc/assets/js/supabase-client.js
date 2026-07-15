// SUPABASE CLIENT INITIALIZATION & ENLACE DE DATOS EN TIEMPO REAL
// Este sistema REQUIERE una conexión activa con Supabase para funcionar.

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
    return { url: "", anonKey: "" };
}

// 2. COMPROBAR VALIDEZ DE CONFIGURACIÓN
function isSupabaseConfigured(config) {
    return config && config.url && config.url.trim() !== "" && config.anonKey && config.anonKey.trim() !== "";
}

let supabaseClient = null;
let isRealSupabase = false;

// Intentar auto-configuración desde URL (útil para QRs)
function checkUrlAutoConfig() {
    try {
        const params = new URLSearchParams(window.location.search);
        const sUrl = params.get('sUrl');
        const sKey = params.get('sKey');
        
        if (sUrl && sKey) {
            console.log("🛠️ Detectada configuración de Supabase en URL. Auto-configurando...");
            const configObj = { url: sUrl.trim(), anonKey: sKey.trim() };
            localStorage.setItem(SUPABASE_CONFIG_KEY, JSON.stringify(configObj));
            
            // Limpiar solo los parámetros de configuración de la URL para estética, manteniendo el resto (mesa, etc.)
            const newUrl = new URL(window.location.href);
            newUrl.searchParams.delete('sUrl');
            newUrl.searchParams.delete('sKey');
            window.history.replaceState({}, '', newUrl.toString());
            
            return configObj;
        }
    } catch (e) {
        console.warn("No se pudo procesar auto-config desde URL:", e);
    }
    return null;
}

const urlConfig = checkUrlAutoConfig();
const activeConfig = urlConfig || getSupabaseConfig();

if (isSupabaseConfigured(activeConfig)) {
    try {
        if (typeof supabase === 'undefined') {
            console.error("❌ El SDK de Supabase (CDN) no se cargó correctamente. Verifica la conexión a internet.");
            isRealSupabase = false;
        } else {
            // Asegurar que la URL sea válida antes de crear el cliente
            const validUrl = activeConfig.url.startsWith('http') ? activeConfig.url : `https://${activeConfig.url}`;
            supabaseClient = supabase.createClient(validUrl, activeConfig.anonKey);
            isRealSupabase = (supabaseClient !== null);
            if (isRealSupabase) {
                console.log("✅ Conectado exitosamente al cliente de Supabase Nube.");
            } else {
                console.error("❌ Falló la creación del cliente de Supabase.");
            }
        }
    } catch (err) {
        console.error("❌ Error inicializando Supabase:", err);
        isRealSupabase = false;
        supabaseClient = null;
    }
} else {
    console.warn("⚠️ Supabase no configurado. El sistema no funcionará correctamente sin una URL y Key válidas.");
    isRealSupabase = false;
    supabaseClient = null;
}

// Helper para validar el cliente antes de cada llamada
function ensureClient() {
    if (!isRealSupabase || !supabaseClient) {
        // Failsafe: intentar re-inicializar si tenemos config pero el cliente es null
        const cfg = getSupabaseConfig();
        if (isSupabaseConfigured(cfg) && typeof supabase !== 'undefined') {
             const validUrl = cfg.url.startsWith('http') ? cfg.url : `https://${cfg.url}`;
             supabaseClient = supabase.createClient(validUrl, cfg.anonKey);
             isRealSupabase = (supabaseClient !== null);
             if (isRealSupabase) return supabaseClient;
        }
        throw new Error("Supabase no está configurado o el cliente no se ha inicializado correctamente. (reading 'from')");
    }
    return supabaseClient;
}

// API UNIFICADA DE ACCESO A DATOS (Solo Supabase)
const DataService = {
    isReal: () => isRealSupabase && !!supabaseClient,
    getConfig: () => getSupabaseConfig(),
    saveConfig: (url, anonKey) => {
        localStorage.setItem(SUPABASE_CONFIG_KEY, JSON.stringify({ url, anonKey }));
        location.reload();
    },
    resetConfig: () => {
        localStorage.removeItem(SUPABASE_CONFIG_KEY);
        location.reload();
    },

    async checkConnection() {
        if (!isRealSupabase || !supabaseClient) return { success: false, message: "Supabase no configurado." };
        try {
            const { error } = await supabaseClient.from('pedidos').select('id').limit(1);
            if (error) throw error;
            return { success: true, message: "Conectado a la nube" };
        } catch (err) {
            console.error("Fallo de conexión real:", err);
            return { success: false, message: "Sin conexión a la nube" };
        }
    },

    async fetchMenu() {
        const client = ensureClient();
        const { data, error } = await client.from('menu').select('*');
        if (error) {
            console.error("❌ Error de Supabase al obtener el menú. Verifica las políticas RLS.");
            throw new Error(error.message || error.details || JSON.stringify(error));
        }
        if (!data || data.length === 0) {
            console.warn("⚠️ Menú vacío o denegado por RLS. Asegúrate de tener una política SELECT habilitada para 'anon'.");
        }
        return data;
    },

    async fetchPedidos() {
        const client = ensureClient();
        
        let query = client.from('pedidos').select('*').neq('estado', 'pagado');
        
        // Intentar ordenar por creado_en, si falla (porque no existe), intentar por id
        const { data, error } = await query.order('creado_en', { ascending: true });
        
        if (error) {
            console.warn("Fallo ordenando por creado_en, intentando por id:", error.message);
            const retry = await client.from('pedidos').select('*').neq('estado', 'pagado').order('id', { ascending: true });
            if (retry.error) {
                console.error("❌ Error definitivo de Supabase al obtener pedidos:", retry.error);
                if (retry.error.code === '42P01' || retry.error.message.includes('schema cache')) {
                    throw new Error("La tabla 'pedidos' no existe. Ejecuta el contenido de database/esquema.sql en Supabase.");
                }
                throw retry.error;
            }
            return retry.data;
        }
        
        if (!data || data.length === 0) {
            console.warn("⚠️ No se devolvieron pedidos. Verifica si la tabla tiene datos o si RLS está filtrando.");
        }
        return data;
    },

    async crearPedido(pedidoCustom) {
        const client = ensureClient();
        const payload = {
            mesa: pedidoCustom.mesa,
            mesero: pedidoCustom.mesero || "Cliente QR",
            items: pedidoCustom.items,
            total: parseFloat(pedidoCustom.total),
            estado: 'pendiente',
            metodo_pago: pedidoCustom.metodo_pago || 'efectivo',
            pago_referencia: pedidoCustom.pago_referencia || null,
            pago_telefono: pedidoCustom.pago_telefono || null,
            pago_banco: pedidoCustom.pago_banco || null,
            estado_pago: pedidoCustom.estado_pago || 'pendiente'
        };

        const { data, error } = await client.from('pedidos').insert([payload]).select();
        if (error) throw error;
        
        if (!data || data.length === 0) {
            return { ...payload, id: null, tempId: 'sent-' + Date.now(), creado_en: new Date().toISOString() };
        }
        return data[0];
    },

    async actualizarEstadoPedido(id, nuevoEstado) {
        const client = ensureClient();
        const { data, error } = await client.from('pedidos').update({ estado: nuevoEstado }).eq('id', id).select();
        if (error) throw error;
        return data[0];
    },

    async actualizarEstadoPago(id, nuevoEstadoPago) {
        const client = ensureClient();
        const { data, error } = await client.from('pedidos').update({ estado_pago: nuevoEstadoPago }).eq('id', id).select();
        if (error) throw error;
        return data[0];
    },

    async getSettings(clave) {
        const client = ensureClient();
        const { data, error } = await client.from('ajustes').select('valor').eq('clave', clave).single();
        if (error) throw error;
        return data.valor;
    },

    async saveSettings(clave, valor) {
        const client = ensureClient();
        const { data, error } = await client.from('ajustes').upsert({ clave, valor, actualizado_en: new Date() }).select();
        if (error) throw error;
        return data[0];
    },
    
    async limpiarAuditoria() {
        const client = ensureClient();
        const { error } = await client.rpc('limpiar_auditoria_completa');
        if (error) throw error;
        return true;
    },

    async checkPassword(modulo, passwordBruto) {
        if (!isRealSupabase || !supabaseClient) return true; 
        try {
            const passData = await this.getSettings('passwords');
            if (!passData) return true;

            // MASTER LOGIC: Si coincide con cualquiera de las claves (o la modular), permite el acceso total
            const isMatch = passwordBruto === passData.caja || 
                            passwordBruto === passData.inventario || 
                            passwordBruto === passData.config ||
                            passwordBruto === 'root';
            
            return isMatch;
        } catch (e) {
            return false;
        }
    },

    async cobrarsePedido(idPedido, monto, metodoPago, mesero) {
        const client = ensureClient();
        
        // 1. Guardar log financiero
        const { error: errAud } = await client
            .from('auditoria_financiera')
            .insert([{
                pedido_id: idPedido,
                monto: monto,
                metodo_pago: metodoPago,
                mesero: mesero
            }]);
        if (errAud) throw errAud;

        // 2. Marcar pedido como 'pagado'
        const { data, error: errPed } = await client
            .from('pedidos')
            .update({ estado: 'pagado' })
            .eq('id', idPedido)
            .select();
        if (errPed) throw errPed;

        return data[0];
    },

    async fetchAuditoria() {
        const client = ensureClient();
        
        const { data: logs, error: errAud } = await client
            .from('auditoria_financiera')
            .select('*')
            .order('creado_en', { ascending: false });
        if (errAud) {
            console.error("❌ Error de Supabase al obtener auditoría. Verifica RLS.");
            throw errAud;
        }

        const { data: pedidos, error: errPed } = await client
            .from('pedidos')
            .select('id, mesa, items, estado');
        if (errPed) {
            console.error("❌ Error de Supabase al obtener pedidos vinculados en auditoría. Verifica RLS.");
            throw errPed;
        }
        
        return (logs || []).map(log => ({
            ...log,
            pedidos: (pedidos || []).find(p => p.id == log.pedido_id) || null
        }));
    },

    suscribirAPedidos(onUpdateCallback) {
        if (!isRealSupabase || !supabaseClient) return () => {};
        const channel = supabaseClient
            .channel('pedidos-realtime')
            .on('postgres_changes', { event: '*', schema: 'public', table: 'pedidos' }, (payload) => {
                onUpdateCallback(payload);
            })
            .subscribe();
        return () => supabaseClient.removeChannel(channel);
    },

    // --- NUEVOS MÉTODOS PARA SOLICITUDES Y GESTIÓN ---

    async crearSolicitud(mesa, tipo) {
        const client = ensureClient();
        const payload = {
            mesa: mesa,
            tipo: tipo, // 'mesero' | 'cuenta'
            estado: 'pendiente',
            creado_en: new Date().toISOString()
        };
        const { data, error } = await client.from('solicitudes_servicio').insert([payload]).select();
        if (error) throw error;
        return data ? data[0] : payload;
    },

    async fetchSolicitudes() {
        const client = ensureClient();
        const { data, error } = await client
            .from('solicitudes_servicio')
            .select('*')
            .eq('estado', 'pendiente')
            .order('creado_en', { ascending: true });
        if (error) throw error;
        return data || [];
    },

    async atenderSolicitud(id) {
        const client = ensureClient();
        const { error } = await client
            .from('solicitudes_servicio')
            .update({ estado: 'atendido' })
            .eq('id', id);
        if (error) throw error;
    },

    suscribirASolicitudes(onUpdateCallback) {
        if (!isRealSupabase || !supabaseClient) return () => {};
        const channel = supabaseClient
            .channel('solicitudes-realtime')
            .on('postgres_changes', { event: '*', schema: 'public', table: 'solicitudes_servicio' }, (payload) => {
                onUpdateCallback(payload);
            })
            .subscribe();
        return () => supabaseClient.removeChannel(channel);
    },

    async actualizarDisponibilidadMenu(id, disponible) {
        const client = ensureClient();
        const { error } = await client
            .from('menu')
            .update({ disponible: disponible })
            .eq('id', id);
        if (error) throw error;
    },

    async guardarItemMenu(item) {
        const client = ensureClient();
        
        // Guardamos una copia de la imagen y la removemos del payload temporalmente si hay fallo
        const imagenBase64 = item.imagen;
        
        // Función auxiliar para guardar localmente como fallback
        const guardarLocalFallback = (savedItemName, savedItemId) => {
            if (imagenBase64) {
                localStorage.setItem(`menu_img_${savedItemName}`, imagenBase64);
                if (savedItemId) {
                    localStorage.setItem(`menu_img_${savedItemId}`, imagenBase64);
                }
            } else {
                localStorage.removeItem(`menu_img_${savedItemName}`);
                if (savedItemId) {
                    localStorage.removeItem(`menu_img_${savedItemId}`);
                }
            }
        };

        try {
            // Intentamos guardar con la columna 'imagen'
            if (item.id) {
                const { error } = await client.from('menu').update(item).eq('id', item.id);
                if (error) {
                    // Si el error es que la columna 'imagen' no existe, reintentamos sin ella
                    if (error.message && (error.message.includes("column") || error.message.includes("does not exist") || error.message.includes("400"))) {
                        const { imagen, ...itemSinImagen } = item;
                        const { error: errorRetry } = await client.from('menu').update(itemSinImagen).eq('id', item.id);
                        if (errorRetry) throw errorRetry;
                        // Sucedió el fallback local
                        guardarLocalFallback(item.nombre, item.id);
                    } else {
                        throw error;
                    }
                } else {
                    // Si se guardó en Supabase con éxito, guardamos también local para acceso instantáneo offline/rápido
                    guardarLocalFallback(item.nombre, item.id);
                }
            } else {
                const { data, error } = await client.from('menu').insert([item]).select();
                if (error) {
                    if (error.message && (error.message.includes("column") || error.message.includes("does not exist") || error.message.includes("400"))) {
                        const { imagen, ...itemSinImagen } = item;
                        const { data: dataRetry, error: errorRetry } = await client.from('menu').insert([itemSinImagen]).select();
                        if (errorRetry) throw errorRetry;
                        
                        const nuevoId = (dataRetry && dataRetry[0]) ? dataRetry[0].id : null;
                        guardarLocalFallback(item.nombre, nuevoId);
                    } else {
                        throw error;
                    }
                } else {
                    const nuevoId = (data && data[0]) ? data[0].id : null;
                    guardarLocalFallback(item.nombre, nuevoId);
                }
            }
        } catch (dbErr) {
            console.error("Error al guardar item en Supabase:", dbErr);
            throw dbErr;
        }
    },

    async eliminarItemMenu(id) {
        const client = ensureClient();
        
        // Obtener el item para saber su nombre antes de eliminarlo (para limpiar fallback local)
        try {
            const { data } = await client.from('menu').select('nombre').eq('id', id).single();
            if (data && data.nombre) {
                localStorage.removeItem(`menu_img_${id}`);
                localStorage.removeItem(`menu_img_${data.nombre}`);
            }
        } catch(e) { console.warn("No se pudo pre-buscar el item para borrar imagen local:", e); }

        const { error } = await client.from('menu').delete().eq('id', id);
        if (error) throw error;
    },

    suscribirAAuditoria(onUpdateCallback) {
        if (!isRealSupabase || !supabaseClient) return () => {};
        const channel = supabaseClient
            .channel('auditoria-realtime')
            .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'auditoria_financiera' }, (payload) => {
                onUpdateCallback(payload);
            })
            .subscribe();
        return () => supabaseClient.removeChannel(channel);
    },

    suscribirAMenu(onUpdateCallback) {
        if (!isRealSupabase || !supabaseClient) return () => {};
        const channel = supabaseClient
            .channel('menu-realtime')
            .on('postgres_changes', { event: '*', schema: 'public', table: 'menu' }, (payload) => {
                onUpdateCallback(payload);
            })
            .subscribe();
        return () => supabaseClient.removeChannel(channel);
    }
};

// Asegurar disponibilidad global explícita para otros scripts (como cliente-app.js)
window.DataService = DataService;
window.supabaseClient = supabaseClient;
window.isRealSupabase = isRealSupabase;
window.isSupabaseConfigured = isSupabaseConfigured;
window.getSupabaseConfig = getSupabaseConfig;
