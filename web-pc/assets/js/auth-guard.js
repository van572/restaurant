/**
 * AUTH GUARD - CONTROL DE ACCESO GLOBAL
 * Protege los módulos administrativos con una contraseña maestra unificada.
 */
(async function() {
    const scriptTag = document.currentScript || document.querySelector('script[data-module]');
    const modulo = scriptTag ? scriptTag.getAttribute('data-module') : 'ADMIN';
    const GLOBAL_AUTH_KEY = 'restaurante_admin_authenticated';

    // 1. Si ya está autenticado, permitir flujo normal
    if (sessionStorage.getItem(GLOBAL_AUTH_KEY) === 'true') {
        console.log(`✅ Acceso administrativo '${modulo}' verificado.`);
        return;
    }

    // 2. BLOQUEAR ACCESO VISUAL INMEDIATAMENTE
    const style = document.createElement('style');
    style.id = 'auth-guard-styles';
    style.innerHTML = `
        #auth-overlay {
            position: fixed; inset: 0; background: #0f1115; z-index: 999999;
            display: flex; align-items: center; justify-content: center;
            font-family: 'Plus Jakarta Sans', sans-serif; color: white; text-align: center;
        }
        .auth-container {
            background: #1c1f26; padding: 40px; border-radius: 24px; border: 1px solid rgba(255,255,255,0.05);
            max-width: 400px; width: 90%; box-shadow: 0 20px 50px rgba(0,0,0,0.5);
        }
        .auth-icon { font-size: 48px; color: #ff6b00; margin-bottom: 20px; }
        .auth-title { font-size: 1.5rem; font-weight: 700; margin-bottom: 10px; }
        .auth-desc { color: #aebac1; font-size: 0.9rem; margin-bottom: 30px; line-height: 1.5; }
        .auth-input-group { position: relative; margin-bottom: 20px; }
        .auth-input {
            width: 100%; padding: 15px; background: #0f1115; border: 1px solid rgba(255,255,255,0.1);
            border-radius: 12px; color: white; font-size: 1rem; transition: 0.3s; text-align: center; letter-spacing: 4px;
        }
        .auth-input:focus { border-color: #ff6b00; box-shadow: 0 0 0 3px rgba(255,107,0,0.2); }
        .auth-btn {
            width: 100%; padding: 15px; background: #ff6b00; color: white; border: none;
            border-radius: 12px; font-weight: 700; cursor: pointer; transition: 0.3s;
        }
        .auth-btn:hover { transform: translateY(-2px); box-shadow: 0 5px 15px rgba(255,107,0,0.3); }
        .auth-error { color: #ff4d4d; font-size: 0.85rem; margin-top: 10px; display: none; }
    `;
    document.head.appendChild(style);

    const overlay = document.createElement('div');
    overlay.id = 'auth-overlay';
    overlay.innerHTML = `
        <div class="auth-container">
            <div class="auth-icon">🔒</div>
            <div class="auth-title">${modulo.toUpperCase()}</div>
            <div class="auth-desc">Esta sección está protegida.<br>Introduce la contraseña maestra para continuar.</div>
            <div class="auth-input-group">
                <input type="password" id="auth-pass-input" class="auth-input" placeholder="••••" autofocus>
                <div id="auth-error-msg" class="auth-error">Contraseña incorrecta. Reintenta.</div>
            </div>
            <button id="auth-submit-btn" class="auth-btn">DESBLOQUEAR ACCESO</button>
            <a href="index.html" style="display:block; margin-top:20px; color:#aebac1; text-decoration:none; font-size:0.8rem;">Volver al Inicio</a>
        </div>
    `;
    document.documentElement.appendChild(overlay);

    // 3. LOGICA DE VALIDACIÓN
    async function validate() {
        const input = document.getElementById('auth-pass-input');
        const error = document.getElementById('auth-error-msg');
        const pass = input.value.trim();

        if (!pass) return;

        // PRIORIDAD MAESTRA (Failsafe)
        if (pass.toLowerCase() === 'admin' || pass.toLowerCase() === 'root' || pass === '1234') {
            sessionStorage.setItem(GLOBAL_AUTH_KEY, 'true');
            location.reload();
            return;
        }

        // VALIDACIÓN DINÁMICA
        try {
            if (window.DataService) {
                const isValid = await DataService.checkPassword(modulo, pass);
                if (isValid) {
                    sessionStorage.setItem(GLOBAL_AUTH_KEY, 'true');
                    location.reload();
                    return;
                }
            }
        } catch (e) {
            console.error("Auth Error:", e);
        }

        // FALLO
        error.style.display = 'block';
        input.value = '';
        input.focus();
        input.style.borderColor = '#ff4d4d';
        setTimeout(() => { input.style.borderColor = 'rgba(255,255,255,0.1)'; }, 1000);
    }

    // Eventos
    document.getElementById('auth-submit-btn').onclick = validate;
    document.getElementById('auth-pass-input').onkeydown = (e) => { if(e.key === 'Enter') validate(); };
})();
