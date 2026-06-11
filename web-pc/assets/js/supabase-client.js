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
            localStorage.setItem(SUPABASE_CONFIG_KEY, JSON.stringify({ url: sUrl, anonKey: sKey }));
            const newUrl = new URL(window.location.href);
            newUrl.searchParams.delete('sUrl');
            newUrl.searchParams.delete('sKey');
            window.history.replaceState({}, '', newUrl.toString());
            return { url: sUrl, anonKey: sKey };
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
        supabaseClient = supabase.createClient(activeConfig.url, activeConfig.anonKey);
        isRealSupabase = true;
        console.log("✅ Conectado exitosamente al cliente de Supabase Nube.");
    } catch (err) {
        console.error("❌ Error inicializando Supabase:", err);
    }
} else {
    console.warn("⚠️ Supabase no configurado. El sistema no funcionará correctamente sin una URL y Key válidas.");
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
        if (error) throw error;
        return data;
    },

    async fetchPedidos() {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        let query = supabaseClient.from('pedidos').select('*').neq('estado', 'pagado');
        const { data, error } = await query.order('creado_en', { ascending: true }).catch(() => {
            return query.order('id', { ascending: true });
        });
        if (error) throw error;
        return data;
    },

    async crearPedido(pedidoCustom) {
        if (!isRealSupabase) throw new Error("Supabase no configurado");
        const payload = {
            mesa: pedidoCustom.mesa,
            mesero: pedidoCustom.mesero || "Cliente QR",
            items: pedidoCustom.items,
            total: parseFloat(pedidoCustom.total),
            estado: 'pendiente'
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
        if (errAud) throw errAud;

        const { data: pedidos, error: errPed } = await supabaseClient
            .from('pedidos')
            .select('id, mesa, items, estado');
        if (errPed) throw errPed;
        
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
