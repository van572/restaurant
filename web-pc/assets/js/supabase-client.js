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
            isRealSupabase = true;
            console.log("✅ Conectado exitosamente al cliente de Supabase Nube.");
        }
    } catch (err) {
        console.error("❌ Error inicializando Supabase:", err);
        isRealSupabase = false;
    }
} else {
    console.warn("⚠️ Supabase no configurado. El sistema no funcionará correctamente sin una URL y Key válidas.");
    isRealSupabase = false;
}

// API UNIFICADA DE ACCESO A DATOS (Solo Supabase)
const DataService = {
    isReal: () => isRealSupabase,
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
        if (!isRealSupabase) return { success: false, message: "Supabase no configurado." };
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
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const { data, error } = await supabaseClient.from('menu').select('*');
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
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        
        let query = supabaseClient.from('pedidos').select('*').neq('estado', 'pagado');
        
        // Intentar ordenar por creado_en, si falla (porque no existe), intentar por id
        const { data, error } = await query.order('creado_en', { ascending: true });
        
        if (error) {
            console.warn("Fallo ordenando por creado_en, intentando por id:", error.message);
            const retry = await supabaseClient.from('pedidos').select('*').neq('estado', 'pagado').order('id', { ascending: true });
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
        if (!isRealSupabase) throw new Error("Supabase no configurado");
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

        const { data, error } = await supabaseClient.from('pedidos').insert([payload]).select();
        if (error) throw error;
        
        if (!data || data.length === 0) {
            return { ...payload, id: null, tempId: 'sent-' + Date.now(), creado_en: new Date().toISOString() };
        }
        return data[0];
    },

    async actualizarEstadoPedido(id, nuevoEstado) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const { data, error } = await supabaseClient.from('pedidos').update({ estado: nuevoEstado }).eq('id', id).select();
        if (error) throw error;
        return data[0];
    },

    async actualizarEstadoPago(id, nuevoEstadoPago) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const { data, error } = await supabaseClient.from('pedidos').update({ estado_pago: nuevoEstadoPago }).eq('id', id).select();
        if (error) throw error;
        return data[0];
    },

    async getSettings(clave) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const { data, error } = await supabaseClient.from('ajustes').select('valor').eq('clave', clave).single();
        if (error) throw error;
        return data.valor;
    },

    async saveSettings(clave, valor) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const { data, error } = await supabaseClient.from('ajustes').upsert({ clave, valor, actualizado_en: new Date() }).select();
        if (error) throw error;
        return data[0];
    },

    async checkPassword(modulo, passwordBruto) {
        if (!isRealSupabase) return true; // Bypass si no hay supabase para pruebas
        try {
            const passData = await this.getSettings('passwords');
            if (!passData) return true; // Sin pass configurada
            return passData[modulo] === passwordBruto;
        } catch (e) {
            return false;
        }
    },

    async cobrarsePedido(idPedido, monto, metodoPago, mesero) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        
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
    },

    async fetchAuditoria() {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        
        const { data: logs, error: errAud } = await supabaseClient
            .from('auditoria_financiera')
            .select('*')
            .order('creado_en', { ascending: false });
        if (errAud) {
            console.error("❌ Error de Supabase al obtener auditoría. Verifica RLS.");
            throw errAud;
        }

        const { data: pedidos, error: errPed } = await supabaseClient
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
        if (!isRealSupabase) return () => {};
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
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const payload = {
            mesa: mesa,
            tipo: tipo, // 'mesero' | 'cuenta'
            estado: 'pendiente',
            creado_en: new Date().toISOString()
        };
        const { data, error } = await supabaseClient.from('solicitudes_servicio').insert([payload]).select();
        if (error) throw error;
        return data ? data[0] : payload;
    },

    async fetchSolicitudes() {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const { data, error } = await supabaseClient
            .from('solicitudes_servicio')
            .select('*')
            .eq('estado', 'pendiente')
            .order('creado_en', { ascending: true });
        if (error) throw error;
        return data || [];
    },

    async atenderSolicitud(id) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const { error } = await supabaseClient
            .from('solicitudes_servicio')
            .update({ estado: 'atendido' })
            .eq('id', id);
        if (error) throw error;
    },

    suscribirASolicitudes(onUpdateCallback) {
        if (!isRealSupabase) return () => {};
        const channel = supabaseClient
            .channel('solicitudes-realtime')
            .on('postgres_changes', { event: '*', schema: 'public', table: 'solicitudes_servicio' }, (payload) => {
                onUpdateCallback(payload);
            })
            .subscribe();
        return () => supabaseClient.removeChannel(channel);
    },

    async actualizarDisponibilidadMenu(id, disponible) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const { error } = await supabaseClient
            .from('menu')
            .update({ disponible: disponible })
            .eq('id', id);
        if (error) throw error;
    },

    async guardarItemMenu(item) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        if (item.id) {
            const { error } = await supabaseClient.from('menu').update(item).eq('id', item.id);
            if (error) throw error;
        } else {
            const { error } = await supabaseClient.from('menu').insert([item]);
            if (error) throw error;
        }
    },

    async eliminarItemMenu(id) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const { error } = await supabaseClient.from('menu').delete().eq('id', id);
        if (error) throw error;
    },

    suscribirAAuditoria(onUpdateCallback) {
        if (!isRealSupabase) return () => {};
        const channel = supabaseClient
            .channel('auditoria-realtime')
            .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'auditoria_financiera' }, (payload) => {
                onUpdateCallback(payload);
            })
            .subscribe();
        return () => supabaseClient.removeChannel(channel);
    },

    suscribirAMenu(onUpdateCallback) {
        if (!isRealSupabase) return () => {};
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
if (typeof supabaseClient !== 'undefined') window.supabaseClient = supabaseClient;
if (typeof supabase !== 'undefined') window.supabase = supabase; // Exportamos la librería base también
window.isRealSupabase = isRealSupabase;
window.isSupabaseConfigured = isSupabaseConfigured;
window.getSupabaseConfig = getSupabaseConfig;
