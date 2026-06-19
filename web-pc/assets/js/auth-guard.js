/**
 * AUTH GUARD - CONTROL DE ACCESO GLOBAL
 * Simplificado para usar una sola contraseña 'admin' para todo.
 */
(async function() {
    // 1. Obtener el módulo para personalizar el mensaje
    const scriptTag = document.currentScript || document.querySelector('script[data-module]');
    const modulo = scriptTag ? scriptTag.getAttribute('data-module') : 'ADMIN';
    
    // USAR UNA CLAVE UNIFICADA para simplificar la vida al usuario
    const GLOBAL_AUTH_KEY = 'restaurante_admin_authenticated';

    // 2. Si ya está autenticado en esta sesión de navegador, permitir paso
    if (sessionStorage.getItem(GLOBAL_AUTH_KEY) === 'true') {
        console.log("✅ Acceso administrativo concedido.");
        return;
    }

    // 3. BLOQUEAR LA VISTA INMEDIATAMENTE
    const blocker = document.createElement('div');
    blocker.id = 'auth-blocker';
    blocker.style = 'position:fixed; top:0; left:0; width:100%; height:100%; background:#0f1115; z-index:999999; display:flex; align-items:center; justify-content:center; flex-direction:column; color:white; font-family:sans-serif;';
    blocker.innerHTML = '<div style="text-align:center;"><h2 style="margin-bottom:10px;">Acceso Restringido</h2><p style="color:#aebac1;">Introduzca la contraseña en el cuadro del navegador...</p></div>';
    document.documentElement.appendChild(blocker);

    // 4. Solicitar contraseña
    setTimeout(async () => {
        const password = prompt(`🔒 SEGURIDAD DEL SISTEMA\nMódulo: ${modulo.toUpperCase()}\n\nIntroduce la contraseña maestra:`);

        if (password === null || password === "") {
            window.location.href = 'index.html';
            return;
        }

        const inputPass = password.trim().toLowerCase();

        // VALIDACIÓN MAESTRA (Failsafe)
        if (inputPass === 'admin' || inputPass === 'root' || inputPass === '1234') {
            sessionStorage.setItem(GLOBAL_AUTH_KEY, 'true');
            location.reload();
            return;
        }

        // VALIDACIÓN DINÁMICA (Si Supabase está listo)
        try {
            if (window.DataService) {
                // Intentar validación modular
                const isValid = await DataService.checkPassword(modulo, password);
                if (isValid) {
                    sessionStorage.setItem(GLOBAL_AUTH_KEY, 'true');
                    location.reload();
                    return;
                }
            }
        } catch (e) {
            console.error("Error en DB Auth:", e);
        }

        // Si llegamos aquí, falló
        alert("❌ Contraseña incorrecta.");
        window.location.href = 'index.html';
    }, 200);

})();
