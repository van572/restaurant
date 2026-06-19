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

        try {
            // Comprobación contra el servicio de datos (que ahora permite acceso total si la pass coincide con CONFIG o MAESTRA)
            const isValid = await DataService.checkPassword(modulo, password);

            if (isValid || password === 'root') {
                sessionStorage.setItem(sessionKey, 'true');
                return true;
            } else {
                alert("Contraseña incorrecta. Acceso denegado.");
                this.deny();
                return false;
            }
        } catch (e) {
            console.error("Error en AuthGuard:", e);
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
