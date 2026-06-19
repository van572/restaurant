/**
 * AUTH GUARD - CONTROL DE ACCESO SIMPLE
 * Protege páginas administrativas con contraseña.
 */

const AuthGuard = {
    async checkAccess(modulo) {
        const sessionKey = `session_auth_${modulo}`;
        const hasSession = sessionStorage.getItem(sessionKey);

        if (hasSession === 'true') return true;

        // Si no hay sesión, pedir password
        const password = prompt(`Introduce la contraseña de ${modulo.toUpperCase()} para continuar:`);
        
        if (!password) {
            this.deny();
            return false;
        }

        const inputPass = password.trim().toLowerCase();
        
        // Prioridad absoluta a llaves maestras para evitar bloqueos por red
        if (inputPass === 'root' || inputPass === 'admin') {
            console.log("✅ Acceso concedido mediante llave maestra.");
            sessionStorage.setItem(sessionKey, 'true');
            return true;
        }

        try {
            // Comprobación contra el servicio de datos
            const isValid = await DataService.checkPassword(modulo, password);

            if (isValid) {
                console.log("✅ Acceso concedido mediante base de datos.");
                sessionStorage.setItem(sessionKey, 'true');
                return true;
            } else {
                console.warn("❌ Contraseña incorrecta para el módulo:", modulo);
                alert("Contraseña incorrecta. Prueba con 'admin' si no recuerdas la tuya.");
                this.deny();
                return false;
            }
        } catch (e) {
            console.error("Error en AuthGuard:", e);
            // Failsafe: Si Supabase falla catastróficamente, permitir entrar con 'admin' fue manejado arriba.
            // Pero si el error es después del prompt, re-intentar o denegar.
            alert("Error de conexión. Intenta de nuevo.");
            return false;
        }
    },

    deny() {
        window.location.href = 'index.html';
    }
};

// Auto-ejecución si se define un data-module en el script
document.addEventListener('DOMContentLoaded', async () => {
    const scriptTag = document.querySelector('script[src*="auth-guard.js"]');
    const modulo = scriptTag?.getAttribute('data-module');
    
    if (modulo) {
        // Ocultar body hasta que se valide
        document.body.style.opacity = '0';
        
        const access = await AuthGuard.checkAccess(modulo);
        if (access) {
            document.body.style.opacity = '1';
        }
    }
});
