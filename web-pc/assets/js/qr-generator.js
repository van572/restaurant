/**
 * LOGICA GENERADOR DE QR
 * Automatiza la creación de etiquetas para las mesas del restaurante.
 * Soporta Código QR Global unificado y Códigos por Lote individuales.
 */

document.addEventListener('DOMContentLoaded', () => {
    // Detectar URL Base actual
    const currentPath = window.location.pathname;
    let basePath = window.location.origin + currentPath.substring(0, currentPath.lastIndexOf('/') + 1);
    const clientUrl = basePath + "cliente.html";
    
    const urlInput = document.getElementById('url-base');
    if (urlInput) {
        urlInput.value = clientUrl;
    }
    const detectedSpan = document.getElementById('detected-url');
    if (detectedSpan) {
        detectedSpan.textContent = clientUrl;
    }

    // Generar el QR Global automáticamente al cargar
    setTimeout(() => {
        generarQRGlobal();
    }, 200);
});

let globalQRCodeObj = null;

function generarQRGlobal() {
    const urlBaseInput = document.getElementById('url-base');
    const urlBase = urlBaseInput ? urlBaseInput.value.trim() : window.location.origin + "/cliente.html";
    const box = document.getElementById('global-qr-box');
    
    if (!box) return;
    box.innerHTML = '';

    // Obtener config de Supabase para inyectar en el QR si existe el Real
    const config = typeof DataService !== 'undefined' ? DataService.getConfig() : null;
    const hasConfig = typeof DataService !== 'undefined' ? DataService.isReal() : false;

    let targetUrl = urlBase;
    if (hasConfig && config) {
        targetUrl += `?sUrl=${encodeURIComponent(config.url)}&sKey=${encodeURIComponent(config.anonKey)}`;
    }

    globalQRCodeObj = new QRCode(box, {
        text: targetUrl,
        width: 180,
        height: 180,
        colorDark : "#000000",
        colorLight : "#ffffff",
        correctLevel : QRCode.CorrectLevel.H
    });
}

function descargarQRGlobal() {
    const box = document.getElementById('global-qr-box');
    if (!box) return;
    const img = box.querySelector('img');
    
    if (img && img.src) {
        const link = document.createElement('a');
        link.href = img.src;
        link.download = `QR_Global_LaCocinaReal.png`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        Toast.success("Descarga Iniciada", "Código QR Global listo.");
    } else {
        Toast.warning("Generando...", "La imagen del QR aún se está creando. Por favor reintenta.");
    }
}

function imprimirQRGlobal() {
    const container = document.getElementById('global-qr-container');
    if (!container) return;
    const box = document.getElementById('global-qr-box');
    const img = box ? box.querySelector('img') : null;
    if (!img || !img.src) {
        Toast.warning("Generando...", "La imagen del QR aún se está creando. Reintenta.");
        return;
    }

    const printWindow = window.open('', '_blank', 'width=500,height=600');
    if (!printWindow) {
        alert("Por favor habilita las ventanas emergentes (pop-ups) para imprimir la etiqueta.");
        return;
    }

    printWindow.document.write(`
        <html>
        <head>
            <style>
                body {
                    font-family: 'Plus Jakarta Sans', sans-serif;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    height: 100vh;
                    margin: 0;
                    background: #fff;
                }
                .ticket-container {
                    border: 3px double #000;
                    padding: 30px;
                    border-radius: 20px;
                    text-align: center;
                    width: 300px;
                }
                .title {
                    font-size: 26px;
                    font-weight: 800;
                    color: #ff6b00;
                    margin-bottom: 5px;
                }
                .subtitle {
                    font-size: 11px;
                    color: #555;
                    font-weight: bold;
                    letter-spacing: 0.5px;
                    margin-bottom: 20px;
                    text-transform: uppercase;
                }
                .qr-img {
                    width: 200px;
                    height: 200px;
                    margin: 10px auto;
                }
                .footer-text {
                    font-size: 13px;
                    font-weight: bold;
                    margin-top: 15px;
                    text-transform: uppercase;
                }
                .sub-footer {
                    font-size: 10px;
                    color: #777;
                    margin-top: 5px;
                    max-width: 220px;
                    margin-left: auto;
                    margin-right: auto;
                    line-height: 1.3;
                }
            </style>
        </head>
        <body onload="window.print(); window.close();">
            <div class="ticket-container">
                <div class="title">FOGÓN GUAROTUYERO</div>
                <div class="subtitle">Menú Digital & Auto-Servicio QR</div>
                <img class="qr-img" src="${img.src}" />
                <div class="footer-text">¡ESCANEAME PARA ORDENAR! 📱</div>
                <div class="sub-footer">Escanea con tu celular, selecciona tu mesa y envía tu comanda directo a cocina.</div>
            </div>
        </body>
        </html>
    `);
    printWindow.document.close();
}

function generarLoteQR() {
    const urlBaseInput = document.getElementById('url-base');
    const urlBase = urlBaseInput ? urlBaseInput.value.trim() : "";
    const inicioInput = document.getElementById('mesa-inicio');
    const finInput = document.getElementById('mesa-fin');
    const inicio = inicioInput ? parseInt(inicioInput.value) : 1;
    const fin = finInput ? parseInt(finInput.value) : 5;
    
    if (!urlBase) {
        Toast.error("Error", "Por favor ingresa una URL válida.");
        return;
    }

    if (isNaN(inicio) || isNaN(fin) || inicio > fin) {
        Toast.error("Rango Inválido", "El rango de mesas no es válido.");
        return;
    }

    const grid = document.getElementById('qr-grid');
    if (!grid) return;
    grid.innerHTML = '';
    
    const resultContainer = document.getElementById('result-container');
    if (resultContainer) resultContainer.style.display = 'block';

    // Obtener config de Supabase para inyectar en el QR si existe el Real
    const config = typeof DataService !== 'undefined' ? DataService.getConfig() : null;
    const hasConfig = typeof DataService !== 'undefined' ? DataService.isReal() : false;

    for(let i = inicio; i <= fin; i++) {
        let tableUrl = `${urlBase}?mesa=${i}`;
        if (hasConfig && config) {
            tableUrl += `&sUrl=${encodeURIComponent(config.url)}&sKey=${encodeURIComponent(config.anonKey)}`;
        }
        crearCardQR(i, tableUrl);
    }
    Toast.success("Generación Exitosa", `Se han creado ${fin - inicio + 1} etiquetas QR.`);
}

function crearCardQR(numeroMesa, url) {
    const grid = document.getElementById('qr-grid');
    if (!grid) return;
    
    const card = document.createElement('div');
    card.className = 'qr-item';
    
    const label = document.createElement('span');
    label.className = 'qr-label';
    label.textContent = `MESA ${numeroMesa}`;
    card.appendChild(label);

    const urlPreview = document.createElement('div');
    urlPreview.className = 'url-preview';
    urlPreview.textContent = url;
    card.appendChild(urlPreview);

    const qrBox = document.createElement('div');
    qrBox.className = 'qr-code-box';
    qrBox.id = `qr-table-${numeroMesa}`;
    card.appendChild(qrBox);

    const btn = document.createElement('button');
    btn.className = 'btn-download';
    btn.innerHTML = '<ion-icon name="download-outline"></ion-icon> Descargar PNG';
    btn.onclick = () => descargarQR(numeroMesa);
    card.appendChild(btn);

    grid.appendChild(card);

    // Generar el QR usando la librería
    new QRCode(qrBox, {
        text: url,
        width: 160,
        height: 160,
        colorDark : "#000000",
        colorLight : "#ffffff",
        correctLevel : QRCode.CorrectLevel.H
    });
}

function descargarQR(numero) {
    const box = document.getElementById(`qr-table-${numero}`);
    if (!box) return;
    const img = box.querySelector('img');
    
    if (img) {
        const link = document.createElement('a');
        link.href = img.src;
        link.download = `QR_Mesa_${numero}.png`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        Toast.info("Descargando", `Mesa ${numero} lista.`);
    } else {
        Toast.warning("Generando...", "La imagen aún se está creando. Reintenta.");
    }
}
